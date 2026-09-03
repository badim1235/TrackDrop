package io.github.badim1235.trackdrop.ranking;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class DailyRankingScheduler {
	private static final Logger LOGGER = LoggerFactory.getLogger(DailyRankingScheduler.class);
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	private final DailyRankingService rankingService;
	private final Clock clock;

	DailyRankingScheduler(DailyRankingService rankingService, Clock clock) {
		this.rankingService = rankingService;
		this.clock = clock;
	}

	@Scheduled(cron = "${trackdrop.ranking.cron:0 5 0 * * *}", zone = "Asia/Seoul")
	void finalizeYesterday() {
		LocalDate targetDate = LocalDate.ofInstant(clock.instant(), SERVICE_ZONE).minusDays(1);
		boolean created = rankingService.snapshot(targetDate);
		if (created) {
			LOGGER.info("Completed daily ranking snapshot for {}", targetDate);
		}
	}
}
