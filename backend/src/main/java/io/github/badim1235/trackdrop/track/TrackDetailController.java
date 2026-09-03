package io.github.badim1235.trackdrop.track;

import io.github.badim1235.trackdrop.identity.TrackDropPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tracks/{trackId}")
public class TrackDetailController {
	private final TrackDetailService trackDetailService;

	TrackDetailController(TrackDetailService trackDetailService) {
		this.trackDetailService = trackDetailService;
	}

	@GetMapping
	TrackDetailResponse get(
		@PathVariable UUID trackId,
		@AuthenticationPrincipal TrackDropPrincipal principal
	) {
		return trackDetailService.get(trackId, principal == null ? null : principal.userId());
	}
}
