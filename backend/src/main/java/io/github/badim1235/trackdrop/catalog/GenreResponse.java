package io.github.badim1235.trackdrop.catalog;

import java.util.List;
import java.util.UUID;

public record GenreResponse(List<Genre> items) {
	public record Genre(UUID id, String code, String displayName, int sortOrder) {
	}
}
