package io.github.badim1235.trackdrop.recommendation;

import io.github.badim1235.trackdrop.catalog.MusicProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RecommendationRequest(
	@NotNull(message = "음악 제공자를 확인해 주세요.")
	MusicProvider provider,
	@NotBlank(message = "추천할 곡을 선택해 주세요.")
	@Size(max = 255, message = "추천할 곡을 다시 선택해 주세요.")
	String externalTrackId,
	@NotNull(message = "대표 장르를 선택해 주세요.")
	UUID primaryGenreId,
	@NotBlank(message = "한줄평을 입력해 주세요.")
	@Size(max = 120, message = "한줄평은 120자 이하로 입력해 주세요.")
	String comment
) {
}
