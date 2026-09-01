package io.github.badim1235.trackdrop.catalog;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;

final class ProviderCallRateLimiter {
	private static final Duration WINDOW = Duration.ofMinutes(1);

	private final int limit;
	private final Clock clock;
	private final ArrayDeque<Instant> calls = new ArrayDeque<>();

	ProviderCallRateLimiter(int limit, Clock clock) {
		this.limit = limit;
		this.clock = clock;
	}

	synchronized void acquire() {
		Instant now = clock.instant();
		Instant cutoff = now.minus(WINDOW);
		while (!calls.isEmpty() && !calls.peekFirst().isAfter(cutoff)) {
			calls.removeFirst();
		}

		if (calls.size() >= limit) {
			long seconds = Math.max(1, Duration.between(now, calls.peekFirst().plus(WINDOW)).toSeconds());
			throw MusicSearchException.rateLimited(seconds);
		}
		calls.addLast(now);
	}
}
