package io.github.badim1235.trackdrop.home;

import io.github.badim1235.trackdrop.identity.TrackDropPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HomeController {
	private final HomeService homeService;

	HomeController(HomeService homeService) {
		this.homeService = homeService;
	}

	@GetMapping("/home")
	HomeResponse home(@AuthenticationPrincipal TrackDropPrincipal principal) {
		return homeService.home(viewerId(principal));
	}

	@GetMapping("/tracks/recent")
	RecentTracksResponse recentTracks(
		@RequestParam(required = false) String cursor,
		@AuthenticationPrincipal TrackDropPrincipal principal
	) {
		return homeService.recent(cursor, viewerId(principal));
	}

	private static UUID viewerId(TrackDropPrincipal principal) {
		return principal == null ? null : principal.userId();
	}
}
