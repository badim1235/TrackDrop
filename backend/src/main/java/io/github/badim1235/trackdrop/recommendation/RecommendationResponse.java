package io.github.badim1235.trackdrop.recommendation;

import io.github.badim1235.trackdrop.catalog.GenreResponse.Genre;
import io.github.badim1235.trackdrop.catalog.MusicProvider;
import io.github.badim1235.trackdrop.shared.quota.DailyQuotaSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RecommendationResponse(
	Track track,
	Recommendation recommendation,
	Vote vote,
	DailyQuotaSnapshot quota
) {
	public record Track(
		UUID id,
		String title,
		String artistName,
		String albumName,
		String albumCoverUrl,
		Integer releaseYear,
		boolean explicit,
		String primaryGenreName,
		Preview preview,
		String externalUrl
	) {
	}

	public record Preview(boolean available, MusicProvider provider, String url) {
	}

	public record Recommendation(
		UUID id,
		Genre primaryGenre,
		String comment,
		Instant createdAt
	) {
	}

	public record Vote(boolean created, LocalDate votedOn) {
	}
}
