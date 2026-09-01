package io.github.badim1235.trackdrop.home;

import io.github.badim1235.trackdrop.catalog.GenreResponse.Genre;
import io.github.badim1235.trackdrop.catalog.MusicProvider;
import io.github.badim1235.trackdrop.shared.quota.DailyQuotaSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HomeResponse(
	Instant asOf,
	DailyQuotaSnapshot quota,
	Section trending,
	Section recent
) {
	public record Section(String title, List<TrackCard> items, String viewAllPath) {
	}

	public record TrackCard(
		UUID id,
		String title,
		String artistName,
		String albumName,
		String albumCoverUrl,
		Integer releaseYear,
		boolean explicit,
		Genre primaryGenre,
		Recommendation recommendation,
		int todayVoteCount,
		Viewer viewer,
		Preview preview,
		List<ExternalLink> externalLinks
	) {
	}

	public record Recommendation(
		UUID id,
		String comment,
		boolean commentAvailable,
		String recommenderNickname,
		Instant createdAt
	) {
	}

	public record Viewer(boolean hasVotedToday) {
	}

	public record Preview(
		boolean available,
		MusicProvider provider,
		String kind,
		String startPosition,
		String url
	) {
	}

	public record ExternalLink(MusicProvider provider, String url) {
	}
}
