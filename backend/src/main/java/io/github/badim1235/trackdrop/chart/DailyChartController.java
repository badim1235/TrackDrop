package io.github.badim1235.trackdrop.chart;

import io.github.badim1235.trackdrop.identity.TrackDropPrincipal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/charts/daily")
public class DailyChartController {
	private final DailyChartService chartService;

	DailyChartController(DailyChartService chartService) {
		this.chartService = chartService;
	}

	@GetMapping
	DailyChartResponse dailyChart(
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
		@RequestParam(defaultValue = "all") String genre,
		@RequestParam(required = false) String cursor,
		@AuthenticationPrincipal TrackDropPrincipal principal
	) {
		UUID viewerId = principal == null ? null : principal.userId();
		return chartService.get(date, genre, cursor, viewerId);
	}
}
