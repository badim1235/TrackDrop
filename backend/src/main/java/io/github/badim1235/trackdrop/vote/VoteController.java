package io.github.badim1235.trackdrop.vote;

import io.github.badim1235.trackdrop.identity.TrackDropPrincipal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tracks/{trackId}/votes")
public class VoteController {
	private final VoteService voteService;

	VoteController(VoteService voteService) {
		this.voteService = voteService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	VoteResponse create(
		@AuthenticationPrincipal TrackDropPrincipal principal,
		@PathVariable UUID trackId
	) {
		return voteService.create(principal.userId(), trackId);
	}
}
