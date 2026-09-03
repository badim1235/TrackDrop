package io.github.badim1235.trackdrop.moderation;

import io.github.badim1235.trackdrop.moderation.ReportResponse.Report;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReportService {
	private final JdbcClient jdbcClient;
	private final Clock clock;
	private final boolean enabled;

	ReportService(
		JdbcClient jdbcClient,
		Clock clock,
		@Value("${trackdrop.features.reports-enabled:false}") boolean enabled
	) {
		this.jdbcClient = jdbcClient;
		this.clock = clock;
		this.enabled = enabled;
	}

	@Transactional
	ReportResponse create(UUID reporterId, UUID recommendationId, ReportRequest request) {
		if (!enabled) {
			throw ReportException.featureDisabled();
		}
		UUID recommenderId = jdbcClient.sql("""
				SELECT recommender_user_id
				FROM recommendations
				WHERE id = :recommendationId
				""")
			.param("recommendationId", recommendationId)
			.query(UUID.class)
			.optional()
			.orElseThrow(ReportException::recommendationNotFound);
		if (recommenderId.equals(reporterId)) {
			throw ReportException.selfReportNotAllowed();
		}

		UUID reportId = UUID.randomUUID();
		Instant createdAt = clock.instant();
		String details = normalizeDetails(request.details());
		try {
			jdbcClient.sql("""
					INSERT INTO content_reports (
						id, reporter_user_id, recommendation_id,
						reason_code, details, status, created_at
					)
					VALUES (
						:id, :reporterId, :recommendationId,
						:reasonCode, :details, 'PENDING', :createdAt
					)
					""")
				.param("id", reportId)
				.param("reporterId", reporterId)
				.param("recommendationId", recommendationId)
				.param("reasonCode", request.reasonCode().name())
				.param("details", details)
				.param("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
				.update();
		} catch (DuplicateKeyException exception) {
			throw ReportException.alreadyReported(exception);
		}
		return new ReportResponse(new Report(reportId, "PENDING", createdAt));
	}

	private static String normalizeDetails(String details) {
		if (details == null || details.isBlank()) {
			return null;
		}
		return details.trim();
	}
}
