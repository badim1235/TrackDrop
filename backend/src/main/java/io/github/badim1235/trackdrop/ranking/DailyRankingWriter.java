package io.github.badim1235.trackdrop.ranking;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class DailyRankingWriter {
	private static final Duration STALE_RUN_TIMEOUT = Duration.ofMinutes(30);
	private static final int SNAPSHOT_SIZE = 50;

	private final JdbcClient jdbcClient;

	DailyRankingWriter(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
	boolean isCompleted(LocalDate rankingDate) {
		return jdbcClient.sql("""
				SELECT EXISTS (
					SELECT 1
					FROM ranking_runs
					WHERE ranking_date = :rankingDate
					  AND status = 'COMPLETED'
				)
				""")
			.param("rankingDate", rankingDate)
			.query(Boolean.class)
			.single();
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	Optional<UUID> claim(LocalDate rankingDate, Instant now) {
		OffsetDateTime startedAt = utc(now);
		OffsetDateTime staleBefore = utc(now.minus(STALE_RUN_TIMEOUT));
		return jdbcClient.sql("""
				INSERT INTO ranking_runs (
					id, ranking_date, status, attempt_count, started_at,
					completed_at, failure_code, created_at, updated_at
				)
				VALUES (
					:id, :rankingDate, 'RUNNING', 1, :startedAt,
					NULL, NULL, :startedAt, :startedAt
				)
				ON CONFLICT (ranking_date) DO UPDATE
				SET status = 'RUNNING',
					attempt_count = ranking_runs.attempt_count + 1,
					started_at = EXCLUDED.started_at,
					completed_at = NULL,
					failure_code = NULL,
					updated_at = EXCLUDED.updated_at
				WHERE ranking_runs.status IN ('PENDING', 'FAILED')
				   OR (
					ranking_runs.status = 'RUNNING'
					AND ranking_runs.started_at < :staleBefore
				   )
				RETURNING id
				""")
			.param("id", UUID.randomUUID())
			.param("rankingDate", rankingDate)
			.param("startedAt", startedAt)
			.param("staleBefore", staleBefore)
			.query(UUID.class)
			.optional();
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void complete(UUID runId, LocalDate rankingDate, Instant now) {
		OffsetDateTime createdAt = utc(now.truncatedTo(ChronoUnit.MILLIS));
		jdbcClient.sql("DELETE FROM daily_rankings WHERE ranking_run_id = :runId")
			.param("runId", runId)
			.update();

		jdbcClient.sql("""
				WITH vote_counts AS (
					SELECT
						vote.track_id,
						recommendation.primary_genre_id AS genre_id,
						COUNT(vote.id)::INTEGER AS vote_count
					FROM votes vote
					JOIN tracks track ON track.id = vote.track_id
					JOIN recommendations recommendation ON recommendation.track_id = track.id
					WHERE vote.voted_on = :rankingDate
					GROUP BY vote.track_id, recommendation.primary_genre_id
				),
				all_ranked AS (
					SELECT
						vote_counts.track_id,
						vote_counts.vote_count,
						ROW_NUMBER() OVER (
							ORDER BY
								vote_counts.vote_count DESC,
								track.title COLLATE trackdrop_nocase ASC,
								track.artist_name COLLATE trackdrop_nocase ASC,
								track.id ASC
						) AS rank
					FROM vote_counts
					JOIN tracks track ON track.id = vote_counts.track_id
				),
				genre_ranked AS (
					SELECT
						vote_counts.genre_id,
						vote_counts.track_id,
						vote_counts.vote_count,
						ROW_NUMBER() OVER (
							PARTITION BY vote_counts.genre_id
							ORDER BY
								vote_counts.vote_count DESC,
								track.title COLLATE trackdrop_nocase ASC,
								track.artist_name COLLATE trackdrop_nocase ASC,
								track.id ASC
						) AS rank
					FROM vote_counts
					JOIN tracks track ON track.id = vote_counts.track_id
				)
				INSERT INTO daily_rankings (
					id, ranking_run_id, ranking_date, scope_type,
					genre_id, track_id, rank, vote_count, created_at
				)
				SELECT
					gen_random_uuid(), :runId, :rankingDate, 'ALL',
					NULL, track_id, rank, vote_count, :createdAt
				FROM all_ranked
				WHERE rank <= :snapshotSize
				UNION ALL
				SELECT
					gen_random_uuid(), :runId, :rankingDate, 'GENRE',
					genre_id, track_id, rank, vote_count, :createdAt
				FROM genre_ranked
				WHERE rank <= :snapshotSize
				""")
			.param("runId", runId)
			.param("rankingDate", rankingDate)
			.param("createdAt", createdAt)
			.param("snapshotSize", SNAPSHOT_SIZE)
			.update();

		int completed = jdbcClient.sql("""
				UPDATE ranking_runs
				SET status = 'COMPLETED',
					completed_at = :completedAt,
					failure_code = NULL,
					updated_at = :completedAt
				WHERE id = :runId
				  AND ranking_date = :rankingDate
				  AND status = 'RUNNING'
				""")
			.param("runId", runId)
			.param("rankingDate", rankingDate)
			.param("completedAt", createdAt)
			.update();
		if (completed != 1) {
			throw new IllegalStateException("The ranking run is no longer claimable");
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void fail(UUID runId, Instant now, String failureCode) {
		jdbcClient.sql("""
				UPDATE ranking_runs
				SET status = 'FAILED',
					failure_code = :failureCode,
					updated_at = :failedAt
				WHERE id = :runId AND status = 'RUNNING'
				""")
			.param("runId", runId)
			.param("failureCode", failureCode)
			.param("failedAt", utc(now))
			.update();
	}

	private static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}
}
