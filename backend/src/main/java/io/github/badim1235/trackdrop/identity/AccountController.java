package io.github.badim1235.trackdrop.identity;

import io.github.badim1235.trackdrop.shared.quota.DailyQuotaService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class AccountController {

	private final IdentityService identityService;
	private final DailyQuotaService quotaService;

	public AccountController(IdentityService identityService, DailyQuotaService quotaService) {
		this.identityService = identityService;
		this.quotaService = quotaService;
	}

	@GetMapping
	AccountResponse me(@AuthenticationPrincipal TrackDropPrincipal principal) {
		return AccountResponse.from(
			identityService.account(principal.userId()),
			quotaService.current(principal.userId()));
	}
}
