package io.github.badim1235.trackdrop.ranking;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DailyRankingService {
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
	private static final String SNAPSHOT_FAILURE_CODE = "SNAPSHOT_WRITE_FAILED";

	private final DailyRankingWriter writer;
	private final Clock clock;

	DailyRankingService(DailyRankingWriter writer, Clock clock) {
		this.writer = writer;
		this.clock = clock;
	}

	public boolean snapshot(LocalDate rankingDate) {
		LocalDate today = LocalDate.ofInstant(clock.instant(), SERVICE_ZONE);
		if (rankingDate == null || !rankingDate.isBefore(today)) {
			throw new IllegalArgumentException("Only a completed service date can be finalized");
		}
		if (writer.isCompleted(rankingDate)) {
			return false;
		}

		Instant startedAt = clock.instant();
		Optional<UUID> claimedRun = writer.claim(rankingDate, startedAt);
		if (claimedRun.isEmpty()) {
			return false;
		}

		UUID runId = claimedRun.get();
		try {
			writer.complete(runId, rankingDate, clock.instant());
			return true;
		} catch (RuntimeException exception) {
			try {
				writer.fail(runId, clock.instant(), SNAPSHOT_FAILURE_CODE);
			} catch (RuntimeException failureUpdateException) {
				exception.addSuppressed(failureUpdateException);
			}
			throw exception;
		}
	}
}
