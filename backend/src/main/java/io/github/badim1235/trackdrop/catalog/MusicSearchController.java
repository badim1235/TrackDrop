package io.github.badim1235.trackdrop.catalog;

import io.github.badim1235.trackdrop.identity.TrackDropPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/music")
public class MusicSearchController {
	private final MusicSearchService musicSearchService;

	MusicSearchController(MusicSearchService musicSearchService) {
		this.musicSearchService = musicSearchService;
	}

	@GetMapping("/search")
	MusicSearchResponse search(
		@AuthenticationPrincipal TrackDropPrincipal principal,
		@RequestParam String query
	) {
		return musicSearchService.search(query, principal.userId());
	}
}
