package io.github.badim1235.trackdrop.vote;

import io.github.badim1235.trackdrop.shared.quota.DailyQuotaSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record VoteResponse(
	Vote vote,
	int todayVoteCount,
	DailyQuotaSnapshot quota
) {
	public record Vote(UUID trackId, LocalDate votedOn, Instant createdAt) {
	}
}
