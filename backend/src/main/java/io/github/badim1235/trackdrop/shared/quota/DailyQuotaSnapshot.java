package io.github.badim1235.trackdrop.shared.quota;

import java.time.Instant;
import java.time.LocalDate;

public record DailyQuotaSnapshot(
	LocalDate date,
	int limit,
	int used,
	int remaining,
	Instant resetAt
) {
}
