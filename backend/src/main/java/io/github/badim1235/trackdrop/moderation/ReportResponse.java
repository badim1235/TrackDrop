package io.github.badim1235.trackdrop.moderation;

import java.time.Instant;
import java.util.UUID;

public record ReportResponse(Report report) {
	public record Report(UUID id, String status, Instant createdAt) {
	}
}
