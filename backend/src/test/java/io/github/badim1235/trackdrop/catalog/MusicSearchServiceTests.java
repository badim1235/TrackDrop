package io.github.badim1235.trackdrop.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
		assertThat(first.items().getFirst().existingTrack().action())
			.isEqualTo(MusicSearchResponse.RecommendationAction.SELECT);
	}

	@Test
	void doesNotCacheEmptyProviderResponses() {
		EmptyThenFoundProvider provider = new EmptyThenFoundProvider();
		MusicSearchService service = new MusicSearchService(
			provider,
			Caffeine.newBuilder().build(),
			(requestedProvider, ids, userId, votedOn) -> Map.of(),
			Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC));
		var userId = java.util.UUID.randomUUID();

		assertThat(service.search("한로로", userId).items()).isEmpty();
		assertThat(service.search("한로로", userId).items()).hasSize(1);
		assertThat(provider.callCount).isEqualTo(2);
	}

	@Test
	void mapsRegistrationStateToTheConfirmedSearchActions() {
		FakeProvider provider = new FakeProvider();
		Clock clock = Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC);
		var trackId = java.util.UUID.randomUUID();
		var userId = java.util.UUID.randomUUID();

		MusicSearchService waiting = new MusicSearchService(
			provider,
			Caffeine.newBuilder().build(),
			(requestedProvider, ids, requestedUser, votedOn) -> Map.of(
				"1234", new CatalogRegistrationLookup.Registration(
					trackId, false, false, LocalDate.of(2026, 9, 4))),
			clock);
		assertThat(waiting.search("한로로", userId).items().getFirst().existingTrack().action())
			.isEqualTo(MusicSearchResponse.RecommendationAction.WAIT);

		MusicSearchService currentChart = new MusicSearchService(
			provider,
			Caffeine.newBuilder().build(),
			(requestedProvider, ids, requestedUser, votedOn) -> Map.of(
				"1234", new CatalogRegistrationLookup.Registration(
					trackId, true, false, LocalDate.of(2026, 9, 6))),
			clock);
		assertThat(currentChart.search("한로로", userId).items().getFirst().existingTrack().action())
			.isEqualTo(MusicSearchResponse.RecommendationAction.VOTE);
	}

	@Test
	void acceptsSingleCharacterQueries() {
		MusicSearchService service = new MusicSearchService(
			new FakeProvider(),
			Caffeine.newBuilder().build(),
			(requestedProvider, ids, userId, votedOn) -> Map.of(),
			Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC));

		assertThat(service.search("곡", java.util.UUID.randomUUID()).items()).hasSize(1);
	}

	@Test
	void rejectsEmptyQueries() {
		MusicSearchService service = new MusicSearchService(
			new FakeProvider(),
			Caffeine.newBuilder().build(),
			(requestedProvider, ids, userId, votedOn) -> Map.of(),
			Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC));

		assertThatThrownBy(() -> service.search(" ", java.util.UUID.randomUUID()))
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

	private static class FakeProvider implements MusicCatalogProvider {
		protected int callCount;

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
			return tracks();
		}

		protected List<MusicCatalogTrack> tracks() {
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

	private static final class EmptyThenFoundProvider extends FakeProvider {
		@Override
		public List<MusicCatalogTrack> search(String query) {
			callCount++;
			return callCount == 1 ? List.of() : tracks();
		}
	}
}
