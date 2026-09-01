package io.github.badim1235.trackdrop.catalog;

public record MusicCatalogTrack(
	String externalTrackId,
	String title,
	String artistName,
	String albumName,
	String albumCoverUrl,
	Integer releaseYear,
	String isrc,
	boolean explicit,
	String primaryGenreName,
	String previewUrl,
	String externalUrl
) {
}
