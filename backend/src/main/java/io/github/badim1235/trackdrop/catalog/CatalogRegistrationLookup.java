package io.github.badim1235.trackdrop.catalog;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

interface CatalogRegistrationLookup {
	Map<String, Registration> find(
		MusicProvider provider,
		Collection<String> externalTrackIds,
		UUID userId,
		LocalDate votedOn
	);

	record Registration(UUID trackId, boolean hasVotedToday) {
	}
}
