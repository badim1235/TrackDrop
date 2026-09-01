package io.github.badim1235.trackdrop.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MusicSearchServiceTests {

	@Test
	void normalizesAndCachesEquivalentQueries() {
		FakeProvider provider = new FakeProvider();
		MusicSearchService service = new MusicSearchService(
			provider,
			Caffeine.newBuilder().build(),
			(requestedProvider, ids, userId, votedOn) -> Map.of(),
			Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC));

		var userId = java.util.UUID.randomUUID();
		var first = service.search("  Radiohead  ", userId);
		var second = service.search("radiohead", userId);

		assertThat(provider.callCount).isEqualTo(1);
		assertThat(second).isEqualTo(first);
		assertThat(first.storefront()).isEqualTo("KR");
		assertThat(first.items().getFirst().explicit()).isTrue();
		assertThat(first.items().getFirst().primaryGenreName()).isEqualTo("Alternative");
		assertThat(first.items().getFirst().preview().available()).isTrue();
		assertThat(first.items().getFirst().existingTrack().registered()).isFalse();
		assertThat(first.items().getFirst().existingTrack().hasVotedToday()).isFalse();
	}

	@Test
	void rejectsQueriesOutsideTheConfirmedLength() {
		MusicSearchService service = new MusicSearchService(
			new FakeProvider(),
			Caffeine.newBuilder().build(),
			(requestedProvider, ids, userId, votedOn) -> Map.of(),
			Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC));

		assertThatThrownBy(() -> service.search("a", java.util.UUID.randomUUID()))
			.isInstanceOf(MusicSearchException.class)
			.extracting(exception -> ((MusicSearchException) exception).getCode())
			.isEqualTo("SEARCH_QUERY_INVALID");
	}

	@Test
	void limitsActualProviderCallsPerMinute() {
		Clock clock = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);
		ProviderCallRateLimiter rateLimiter = new ProviderCallRateLimiter(15, clock);
		for (int index = 0; index < 15; index++) {
			rateLimiter.acquire();
		}

		assertThatThrownBy(rateLimiter::acquire)
			.isInstanceOf(MusicSearchException.class)
			.extracting(exception -> ((MusicSearchException) exception).getCode())
			.isEqualTo("RATE_LIMITED");
	}

	private static final class FakeProvider implements MusicCatalogProvider {
		private int callCount;

		@Override
		public MusicProvider provider() {
			return MusicProvider.APPLE_MUSIC;
		}

		@Override
		public String storefront() {
			return "KR";
		}

		@Override
		public List<MusicCatalogTrack> search(String query) {
			callCount++;
			return List.of(new MusicCatalogTrack(
				"1234",
				"Creep",
				"Radiohead",
				"Pablo Honey",
				"https://example.com/cover.jpg",
				1993,
				null,
				true,
				"Alternative",
				"https://example.com/preview.m4a",
				"https://music.apple.com/kr/album/creep/1234"));
		}

		@Override
		public Optional<MusicCatalogTrack> lookup(String externalTrackId) {
			return search(externalTrackId).stream().findFirst();
		}
	}
}
