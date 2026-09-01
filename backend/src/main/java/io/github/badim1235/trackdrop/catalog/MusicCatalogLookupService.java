package io.github.badim1235.trackdrop.catalog;

import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class MusicCatalogLookupService {
	private final MusicCatalogProvider provider;

	MusicCatalogLookupService(MusicCatalogProvider provider) {
		this.provider = provider;
	}

	public Optional<MusicCatalogTrack> lookup(MusicProvider requestedProvider, String externalTrackId) {
		if (requestedProvider != provider.provider()) {
			return Optional.empty();
		}
		return provider.lookup(externalTrackId);
	}
}
