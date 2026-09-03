package io.github.badim1235.trackdrop.moderation;

import io.github.badim1235.trackdrop.identity.TrackDropPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations/{recommendationId}/reports")
public class ReportController {
	private final ReportService reportService;

	ReportController(ReportService reportService) {
		this.reportService = reportService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ReportResponse create(
		@PathVariable UUID recommendationId,
		@AuthenticationPrincipal TrackDropPrincipal principal,
		@Valid @RequestBody ReportRequest request
	) {
		return reportService.create(principal == null ? null : principal.userId(), recommendationId, request);
	}
}
