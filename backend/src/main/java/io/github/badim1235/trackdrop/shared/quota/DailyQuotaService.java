package io.github.badim1235.trackdrop.shared.quota;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyQuotaService {
	private static final int DAILY_LIMIT = 4;
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	private final JdbcClient jdbcClient;
	private final Clock clock;

	DailyQuotaService(JdbcClient jdbcClient, Clock clock) {
		this.jdbcClient = jdbcClient;
		this.clock = clock;
	}

	public DailyQuotaSnapshot current(UUID userId) {
		LocalDate today = LocalDate.now(clock.withZone(SERVICE_ZONE));
		int used = jdbcClient.sql("""
				SELECT used_count
				FROM daily_recommendation_quotas
				WHERE user_id = :userId AND quota_date = :today
				""")
			.param("userId", userId)
			.param("today", today)
			.query(Integer.class)
			.optional()
			.orElse(0);
		return snapshot(today, used);
	}

	@Transactional
	public DailyQuotaSnapshot consume(UUID userId) {
		Instant now = clock.instant();
		LocalDate today = LocalDate.ofInstant(now, SERVICE_ZONE);
		OffsetDateTime timestamp = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
		jdbcClient.sql("""
				INSERT INTO daily_recommendation_quotas (
					user_id, quota_date, daily_limit, used_count, updated_at
				)
				VALUES (:userId, :today, :dailyLimit, 0, :now)
				ON CONFLICT (user_id, quota_date) DO NOTHING
				""")
			.param("userId", userId)
			.param("today", today)
			.param("dailyLimit", DAILY_LIMIT)
			.param("now", timestamp)
			.update();

		int updated = jdbcClient.sql("""
				UPDATE daily_recommendation_quotas
				SET used_count = used_count + 1, updated_at = :now
				WHERE user_id = :userId
				  AND quota_date = :today
				  AND used_count < daily_limit
				""")
			.param("userId", userId)
			.param("today", today)
			.param("now", timestamp)
			.update();
		if (updated == 0) {
			throw new DailyQuotaExceededException(current(userId));
		}
		return current(userId);
	}

	private static DailyQuotaSnapshot snapshot(LocalDate date, int used) {
		return new DailyQuotaSnapshot(
			date,
			DAILY_LIMIT,
			used,
			DAILY_LIMIT - used,
			date.plusDays(1).atStartOfDay(SERVICE_ZONE).toInstant());
	}
}
