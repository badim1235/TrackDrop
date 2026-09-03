package io.github.badim1235.trackdrop.recommendation;

import java.time.LocalDate;
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

	static RecommendationException providerGenreUnavailable() {
		return new RecommendationException(
			"PROVIDER_GENRE_UNAVAILABLE",
			"Apple Music 장르를 확인할 수 없습니다.",
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

	static RecommendationException recommendationCooldown(
		UUID trackId,
		UUID recommendationId,
		LocalDate availableOn
	) {
		Map<String, Object> details = recommendationDetails(trackId, recommendationId);
		details.put("recommendationAvailableOn", availableOn.toString());
		return new RecommendationException(
			"RECOMMENDATION_COOLDOWN",
			"최근 추천된 곡입니다. " + availableOn.getMonthValue() + "월 "
				+ availableOn.getDayOfMonth() + "일부터 다시 추천할 수 있습니다.",
			HttpStatus.CONFLICT,
			Map.copyOf(details));
	}

	static RecommendationException alreadyInCurrentChart(UUID trackId, UUID recommendationId) {
		return new RecommendationException(
			"ALREADY_IN_CURRENT_CHART",
			"현재 차트에 등록된 곡입니다.",
			HttpStatus.CONFLICT,
			Map.copyOf(recommendationDetails(trackId, recommendationId)));
	}

	private static Map<String, Object> recommendationDetails(UUID trackId, UUID recommendationId) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("existingTrackId", trackId.toString());
		if (recommendationId != null) {
			details.put("existingRecommendationId", recommendationId.toString());
		}
		details.put("quotaConsumed", false);
		return details;
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
