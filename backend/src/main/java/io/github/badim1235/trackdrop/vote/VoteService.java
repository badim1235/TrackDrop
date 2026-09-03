package io.github.badim1235.trackdrop.vote;

import io.github.badim1235.trackdrop.shared.quota.DailyQuotaService;
import io.github.badim1235.trackdrop.shared.quota.DailyQuotaSnapshot;
import io.github.badim1235.trackdrop.vote.VoteResponse.Vote;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class VoteService {
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	private final JdbcClient jdbcClient;
	private final DailyQuotaService quotaService;
	private final Clock clock;

	VoteService(JdbcClient jdbcClient, DailyQuotaService quotaService, Clock clock) {
		this.jdbcClient = jdbcClient;
		this.quotaService = quotaService;
		this.clock = clock;
	}

	@Transactional
	VoteResponse create(UUID userId, UUID trackId) {
		Instant now = clock.instant();
		LocalDate today = LocalDate.ofInstant(now, SERVICE_ZONE);
		OffsetDateTime timestamp = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
		TrackVoteState state = findTrackState(trackId, today)
			.orElseThrow(VoteException::trackNotFound);
		if (!state.inCurrentChart()) {
			LocalDate availableOn = state.lastRecommendedOn().plusDays(3);
			if (today.isBefore(availableOn)) {
				throw VoteException.recommendationCooldown(trackId, availableOn);
			}
			throw VoteException.recommendationRequired(trackId);
		}
		DailyQuotaSnapshot quotaBefore = quotaService.current(userId);
		try {
			jdbcClient.sql("""
					INSERT INTO votes (id, user_id, track_id, voted_on, created_at)
					VALUES (:id, :userId, :trackId, :today, :now)
					""")
				.param("id", UUID.randomUUID())
				.param("userId", userId)
				.param("trackId", trackId)
				.param("today", today)
				.param("now", timestamp)
				.update();
		} catch (DataIntegrityViolationException exception) {
			throw VoteException.alreadyVoted(
				trackId, today, quotaBefore, exception);
		}

		DailyQuotaSnapshot quota = quotaService.consume(userId);
		int todayVoteCount = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM votes
				WHERE track_id = :trackId AND voted_on = :today
				""")
			.param("trackId", trackId)
			.param("today", today)
			.query(Long.class)
			.single()
			.intValue();

		return new VoteResponse(
			new Vote(trackId, today, now),
			todayVoteCount,
			quota);
	}

	private Optional<TrackVoteState> findTrackState(UUID trackId, LocalDate today) {
		return jdbcClient.sql("""
				SELECT
					latest.recommended_on,
					EXISTS (
						SELECT 1 FROM votes current_vote
						WHERE current_vote.track_id = track.id
						  AND current_vote.voted_on = :today
					) AS in_current_chart
				FROM tracks track
				JOIN LATERAL (
					SELECT recommendation.recommended_on
					FROM recommendations recommendation
					WHERE recommendation.track_id = track.id
					ORDER BY recommendation.recommended_on DESC, recommendation.created_at DESC, recommendation.id DESC
					LIMIT 1
				) latest ON TRUE
				WHERE track.id = :trackId
				FOR UPDATE OF track
				""")
			.param("trackId", trackId)
			.param("today", today)
			.query((row, rowNumber) -> new TrackVoteState(
				row.getObject("recommended_on", LocalDate.class),
				row.getBoolean("in_current_chart")))
			.optional();
	}

	private record TrackVoteState(LocalDate lastRecommendedOn, boolean inCurrentChart) {
	}
}
