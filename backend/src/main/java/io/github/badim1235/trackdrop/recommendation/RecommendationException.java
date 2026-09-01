package io.github.badim1235.trackdrop.recommendation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class RecommendationException extends RuntimeException {
	private final String code;
	private final HttpStatus status;
	private final Map<String, Object> details;

	private RecommendationException(
		String code,
		String message,
		HttpStatus status,
		Map<String, Object> details
	) {
		super(message);
		this.code = code;
		this.status = status;
		this.details = details;
	}

	static RecommendationException trackNotFound() {
		return new RecommendationException(
			"TRACK_NOT_FOUND",
			"Apple Music에서 해당 곡을 확인할 수 없습니다.",
			HttpStatus.BAD_REQUEST,
			Map.of());
	}

	static RecommendationException genreNotFound() {
		return new RecommendationException(
			"GENRE_INACTIVE_OR_NOT_FOUND",
			"선택할 수 없는 장르입니다.",
			HttpStatus.BAD_REQUEST,
			Map.of());
	}

	static RecommendationException invalidComment() {
		return new RecommendationException(
			"VALIDATION_FAILED",
			"한줄평은 1~120자로 입력해 주세요.",
			HttpStatus.BAD_REQUEST,
			Map.of());
	}

	static RecommendationException alreadyRecommended(UUID trackId, UUID recommendationId) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("existingTrackId", trackId.toString());
		if (recommendationId != null) {
			details.put("existingRecommendationId", recommendationId.toString());
		}
		details.put("quotaConsumed", false);
		return new RecommendationException(
			"ALREADY_RECOMMENDED",
			"이미 등록된 곡입니다.",
			HttpStatus.CONFLICT,
			Map.copyOf(details));
	}

	public String getCode() {
		return code;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public Map<String, Object> getDetails() {
		return details;
	}
}
