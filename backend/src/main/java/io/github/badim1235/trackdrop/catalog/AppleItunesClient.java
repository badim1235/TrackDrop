package io.github.badim1235.trackdrop.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
final class AppleItunesClient implements MusicCatalogProvider {
	private final RestClient restClient;
	private final AppleItunesProperties properties;
	private final ProviderCallRateLimiter rateLimiter;
	private final ObjectMapper objectMapper;

	AppleItunesClient(
		RestClient appleItunesRestClient,
		AppleItunesProperties properties,
		ProviderCallRateLimiter rateLimiter,
		ObjectMapper objectMapper
	) {
		this.restClient = appleItunesRestClient;
		this.properties = properties;
		this.rateLimiter = rateLimiter;
		this.objectMapper = objectMapper;
	}

	@Override
	public MusicProvider provider() {
		return MusicProvider.APPLE_MUSIC;
	}

	@Override
	public String storefront() {
		return properties.storefront();
	}

	@Override
	public List<MusicCatalogTrack> search(String query) {
		return fetch(searchUri(query));
	}

	@Override
	public Optional<MusicCatalogTrack> lookup(String externalTrackId) {
		return fetch(lookupUri(externalTrackId)).stream()
			.filter(track -> track.externalTrackId().equals(externalTrackId))
			.findFirst();
	}

	private List<MusicCatalogTrack> fetch(URI uri) {
		rateLimiter.acquire();
		try {
			String body = restClient.get()
				.uri(uri)
				.retrieve()
				.onStatus(HttpStatusCode::isError, (request, response) -> {
					throw MusicSearchException.providerUnavailable();
				})
				.body(String.class);
			if (body == null) {
				throw MusicSearchException.providerUnavailable();
			}
			return mapTracks(objectMapper.readValue(body, AppleSearchPayload.class));
		} catch (MusicSearchException exception) {
			throw exception;
		} catch (RestClientException | JacksonException exception) {
			throw MusicSearchException.providerUnavailable();
		}
	}

	private URI searchUri(String query) {
		return UriComponentsBuilder.fromUri(properties.baseUrl())
			.path("/search")
			.queryParam("term", "{term}")
			.queryParam("country", properties.storefront())
			.queryParam("media", "music")
			.queryParam("entity", "song")
			.queryParam("limit", properties.resultLimit())
			.queryParam("explicit", "Yes")
			.queryParam("lang", "en_us")
			.encode(StandardCharsets.UTF_8)
			.buildAndExpand(Map.of("term", query))
			.toUri();
	}

	private URI lookupUri(String externalTrackId) {
		return UriComponentsBuilder.fromUri(properties.baseUrl())
			.path("/lookup")
			.queryParam("id", "{id}")
			.queryParam("country", properties.storefront())
			.queryParam("entity", "song")
			.queryParam("lang", "en_us")
			.encode(StandardCharsets.UTF_8)
			.buildAndExpand(Map.of("id", externalTrackId))
			.toUri();
	}

	private List<MusicCatalogTrack> mapTracks(AppleSearchPayload payload) {
		if (payload == null || payload.results() == null) {
			return List.of();
		}

		Map<String, MusicCatalogTrack> tracks = new LinkedHashMap<>();
		for (AppleTrack result : payload.results()) {
			if (!isSong(result)) {
				continue;
			}
			String externalTrackId = result.trackId().toString();
			tracks.putIfAbsent(externalTrackId, new MusicCatalogTrack(
				externalTrackId,
				result.trackName().strip(),
				result.artistName().strip(),
				nullIfBlank(result.collectionName()),
				nullIfBlank(result.artworkUrl100()),
				releaseYear(result.releaseDate()),
				null,
				"explicit".equalsIgnoreCase(result.trackExplicitness()),
				nullIfBlank(result.primaryGenreName()),
				nullIfBlank(result.previewUrl()),
				nullIfBlank(result.trackViewUrl())));
			if (tracks.size() == properties.resultLimit()) {
				break;
			}
		}
		return List.copyOf(tracks.values());
	}

	private static boolean isSong(AppleTrack track) {
		return track != null
			&& track.trackId() != null
			&& !isBlank(track.trackName())
			&& !isBlank(track.artistName())
			&& "song".equalsIgnoreCase(track.kind());
	}

	private static Integer releaseYear(String releaseDate) {
		if (releaseDate == null || releaseDate.length() < 4) {
			return null;
		}
		try {
			return Integer.valueOf(releaseDate.substring(0, 4));
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private static String nullIfBlank(String value) {
		return isBlank(value) ? null : value.strip();
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record AppleSearchPayload(List<AppleTrack> results) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record AppleTrack(
		String kind,
		Long trackId,
		String trackName,
		String artistName,
		String collectionName,
		String artworkUrl100,
		String trackViewUrl,
		String previewUrl,
		String trackExplicitness,
		String primaryGenreName,
		String releaseDate
	) {
	}
}
