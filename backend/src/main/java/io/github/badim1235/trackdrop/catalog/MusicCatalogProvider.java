package io.github.badim1235.trackdrop.catalog;

import java.util.List;
import java.util.Optional;

interface MusicCatalogProvider {
	MusicProvider provider();

	String storefront();

	List<MusicCatalogTrack> search(String query);

	Optional<MusicCatalogTrack> lookup(String externalTrackId);
}
