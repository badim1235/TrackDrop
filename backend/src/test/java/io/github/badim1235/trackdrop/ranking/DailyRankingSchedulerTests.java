package io.github.badim1235.trackdrop.ranking;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DailyRankingSchedulerTests {
	@Test
	void finalizesThePreviousKoreanServiceDate() {
		DailyRankingService rankingService = mock(DailyRankingService.class);
		Clock clock = Clock.fixed(Instant.parse("2026-09-01T15:00:00Z"), ZoneOffset.UTC);
		DailyRankingScheduler scheduler = new DailyRankingScheduler(rankingService, clock);

		scheduler.finalizeYesterday();

		verify(rankingService).snapshot(LocalDate.of(2026, 9, 1));
	}
}
