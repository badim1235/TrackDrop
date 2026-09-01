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
		if (!trackExists(trackId)) {
			throw VoteException.trackNotFound();
		}

		Instant now = clock.instant();
		LocalDate today = LocalDate.ofInstant(now, SERVICE_ZONE);
		OffsetDateTime timestamp = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
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

	private boolean trackExists(UUID trackId) {
		return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM tracks WHERE id = :trackId)")
			.param("trackId", trackId)
			.query(Boolean.class)
			.single();
	}
}
