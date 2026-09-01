package io.github.badim1235.trackdrop.identity;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthRateLimiter {

	private static final int AUTH_REQUEST_LIMIT = 30;
	private static final int SIGNUP_SUCCESS_LIMIT = 5;
	private static final Duration SIGNUP_WINDOW = Duration.ofHours(1);
	private static final Duration SIGNUP_BLOCK = Duration.ofHours(24);

	private final JdbcClient jdbcClient;

	public AuthRateLimiter(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void checkAuthRequest(String ipHash) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		OffsetDateTime windowStart = now.truncatedTo(ChronoUnit.MINUTES);
		int count = jdbcClient.sql("""
				INSERT INTO auth_ip_request_limits (ip_hash, window_started_at, request_count, updated_at)
				VALUES (:ipHash, :windowStart, 1, :now)
				ON CONFLICT (ip_hash) DO UPDATE SET
					window_started_at = CASE
						WHEN auth_ip_request_limits.window_started_at < :windowStart THEN :windowStart
						ELSE auth_ip_request_limits.window_started_at
					END,
					request_count = CASE
						WHEN auth_ip_request_limits.window_started_at < :windowStart THEN 1
						ELSE auth_ip_request_limits.request_count + 1
					END,
					updated_at = :now
				RETURNING request_count
				""")
			.param("ipHash", ipHash)
			.param("windowStart", windowStart)
			.param("now", now)
			.query(Integer.class)
			.single();

		if (count > AUTH_REQUEST_LIMIT) {
			throw IdentityException.rateLimited(Duration.between(now, windowStart.plus(1, ChronoUnit.MINUTES)).toSeconds());
		}
	}

	void lockAndCheckSignup(String ipHash) {
		jdbcClient.sql("SELECT pg_advisory_xact_lock(hashtextextended(:ipHash, 0))")
			.param("ipHash", ipHash)
			.query((resultSet, rowNumber) -> resultSet.getObject(1))
			.single();

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		Optional<OffsetDateTime> blockedUntil = jdbcClient.sql("""
				SELECT blocked_until
				FROM signup_ip_blocks
				WHERE ip_hash = :ipHash AND blocked_until > :now
				""")
			.param("ipHash", ipHash)
			.param("now", now)
			.query(OffsetDateTime.class)
			.optional();

		if (blockedUntil.isPresent()) {
			throw IdentityException.signupBlocked(secondsUntil(now, blockedUntil.get()));
		}

		long successes = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM signup_ip_events
				WHERE ip_hash = :ipHash AND created_at >= :since
				""")
			.param("ipHash", ipHash)
			.param("since", now.minus(SIGNUP_WINDOW))
			.query(Long.class)
			.single();

		if (successes >= SIGNUP_SUCCESS_LIMIT) {
			OffsetDateTime until = now.plus(SIGNUP_BLOCK);
			jdbcClient.sql("""
					INSERT INTO signup_ip_blocks (ip_hash, blocked_until, updated_at)
					VALUES (:ipHash, :until, :now)
					ON CONFLICT (ip_hash) DO UPDATE SET blocked_until = :until, updated_at = :now
					""")
				.param("ipHash", ipHash)
				.param("until", until)
				.param("now", now)
				.update();
			throw IdentityException.signupBlocked(SIGNUP_BLOCK.toSeconds());
		}
	}

	void recordSuccessfulSignup(String ipHash) {
		jdbcClient.sql("INSERT INTO signup_ip_events (ip_hash) VALUES (:ipHash)")
			.param("ipHash", ipHash)
			.update();
	}

	private static long secondsUntil(OffsetDateTime now, OffsetDateTime until) {
		return Math.max(1, Duration.between(now, until).toSeconds());
	}
}
