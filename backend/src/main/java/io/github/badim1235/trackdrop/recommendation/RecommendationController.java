package io.github.badim1235.trackdrop.recommendation;

import io.github.badim1235.trackdrop.identity.TrackDropPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
	private final RecommendationService recommendationService;

	RecommendationController(RecommendationService recommendationService) {
		this.recommendationService = recommendationService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	RecommendationResponse create(
		@AuthenticationPrincipal TrackDropPrincipal principal,
		@Valid @RequestBody RecommendationRequest request
	) {
		return recommendationService.create(principal.userId(), request);
	}
}
