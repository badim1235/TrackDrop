package io.github.badim1235.trackdrop.catalog;

import java.util.List;

public record MusicSearchResponse(
	MusicProvider provider,
	String storefront,
	String attribution,
	List<MusicSearchItem> items
) {
	public record MusicSearchItem(
		MusicProvider provider,
		String externalTrackId,
		String title,
		String artistName,
		String albumName,
		String albumCoverUrl,
		Integer releaseYear,
		String isrc,
		boolean explicit,
		String primaryGenreName,
		Preview preview,
		String externalUrl,
		ExistingTrack existingTrack
	) {
	}

	public record Preview(
		boolean available,
		MusicProvider provider,
		String kind,
		String startPosition,
		String url
	) {
	}

	public record ExistingTrack(boolean registered, String trackId, boolean hasVotedToday) {
	}
}
