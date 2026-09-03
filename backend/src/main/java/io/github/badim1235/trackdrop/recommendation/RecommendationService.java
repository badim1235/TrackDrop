package io.github.badim1235.trackdrop.recommendation;

import io.github.badim1235.trackdrop.catalog.MusicCatalogLookupService;
import io.github.badim1235.trackdrop.catalog.MusicCatalogTrack;
import java.text.Normalizer;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {
	private final MusicCatalogLookupService catalogLookup;
	private final RecommendationWriter writer;

	RecommendationService(
		MusicCatalogLookupService catalogLookup,
		RecommendationWriter writer
	) {
		this.catalogLookup = catalogLookup;
		this.writer = writer;
	}

	public RecommendationResponse create(UUID userId, RecommendationRequest request) {
		String externalTrackId = request.externalTrackId().strip();
		String comment = normalizeComment(request.comment());
		MusicCatalogTrack track = catalogLookup.lookup(request.provider(), externalTrackId)
			.orElseThrow(RecommendationException::trackNotFound);
		return writer.create(
			userId,
			request.provider(),
			track,
			comment);
	}

	private static String normalizeComment(String rawComment) {
		String comment = Normalizer.normalize(rawComment, Normalizer.Form.NFKC).strip();
		int length = comment.codePointCount(0, comment.length());
		if (length < 1 || length > 120) {
			throw RecommendationException.invalidComment();
		}
		return comment;
	}
}
