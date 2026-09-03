package io.github.badim1235.trackdrop.catalog;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.badim1235.trackdrop.catalog.CatalogRegistrationLookup.Registration;
import io.github.badim1235.trackdrop.catalog.MusicSearchResponse.ExistingTrack;
import io.github.badim1235.trackdrop.catalog.MusicSearchResponse.MusicSearchItem;
import io.github.badim1235.trackdrop.catalog.MusicSearchResponse.Preview;
import io.github.badim1235.trackdrop.catalog.MusicSearchResponse.RecommendationAction;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MusicSearchService {
	private static final String ATTRIBUTION = "Music preview provided courtesy of iTunes";
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	private final MusicCatalogProvider provider;
	private final Cache<String, List<MusicCatalogTrack>> cache;
	private final CatalogRegistrationLookup registrations;
	private final Clock clock;

	MusicSearchService(
		MusicCatalogProvider provider,
		Cache<String, List<MusicCatalogTrack>> cache,
		CatalogRegistrationLookup registrations,
		Clock clock
	) {
		this.provider = provider;
		this.cache = cache;
		this.registrations = registrations;
		this.clock = clock;
	}

	public MusicSearchResponse search(String rawQuery, UUID userId) {
		String query = normalize(rawQuery);
		int length = query.codePointCount(0, query.length());
		if (length < 1 || length > 100) {
			throw MusicSearchException.invalidQuery();
		}

		String cacheKey = provider.storefront() + ':' + query.toLowerCase(Locale.ROOT);
		List<MusicCatalogTrack> tracks = cache.getIfPresent(cacheKey);
		if (tracks == null) {
			tracks = provider.search(query);
			if (!tracks.isEmpty()) {
				cache.put(cacheKey, tracks);
			}
		}
		LocalDate today = LocalDate.now(clock.withZone(SERVICE_ZONE));
		Map<String, Registration> registeredTracks = registrations.find(
			provider.provider(),
			tracks.stream().map(MusicCatalogTrack::externalTrackId).toList(),
			userId,
			today);
		return new MusicSearchResponse(
			provider.provider(),
			provider.storefront(),
			ATTRIBUTION,
			tracks.stream().map(track -> toResponse(track, registeredTracks, today)).toList());
	}

	private MusicSearchItem toResponse(
		MusicCatalogTrack track,
		Map<String, Registration> registeredTracks,
		LocalDate today
	) {
		Registration registration = registeredTracks.get(track.externalTrackId());
		return new MusicSearchItem(
			provider.provider(),
			track.externalTrackId(),
			track.title(),
			track.artistName(),
			track.albumName(),
			track.albumCoverUrl(),
			track.releaseYear(),
			track.isrc(),
			track.explicit(),
			track.primaryGenreName(),
			new Preview(
				track.previewUrl() != null,
				provider.provider(),
				"OFFICIAL_30_SECOND_CLIP",
				"PROVIDER_SELECTED",
				track.previewUrl()),
			track.externalUrl(),
			new ExistingTrack(
				registration != null,
				registration == null ? null : registration.trackId().toString(),
				registration != null && registration.inCurrentChart(),
				registration != null && registration.hasVotedToday(),
				registration == null ? null : registration.recommendationAvailableOn(),
				action(registration, today)));
	}

	private static RecommendationAction action(Registration registration, LocalDate today) {
		if (registration == null) {
			return RecommendationAction.SELECT;
		}
		if (registration.hasVotedToday()) {
			return RecommendationAction.VOTED;
		}
		if (registration.inCurrentChart()) {
			return RecommendationAction.VOTE;
		}
		return today.isBefore(registration.recommendationAvailableOn())
			? RecommendationAction.WAIT
			: RecommendationAction.SELECT;
	}

	private static String normalize(String value) {
		if (value == null) {
			return "";
		}
		return Normalizer.normalize(value, Normalizer.Form.NFKC)
			.strip()
			.replaceAll("\\s+", " ");
	}
}
