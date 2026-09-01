package io.github.badim1235.trackdrop.catalog;

import io.github.badim1235.trackdrop.catalog.CatalogRegistrationLookup.Registration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
final class JdbcCatalogRegistrationLookup implements CatalogRegistrationLookup {
	private final JdbcClient jdbcClient;

	JdbcCatalogRegistrationLookup(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public Map<String, Registration> find(
		MusicProvider provider,
		Collection<String> externalTrackIds,
		UUID userId,
		LocalDate votedOn
	) {
		if (externalTrackIds.isEmpty()) {
			return Map.of();
		}

		Map<String, Registration> registrations = new LinkedHashMap<>();
		jdbcClient.sql("""
				SELECT
				  provider_ref.external_track_id,
				  provider_ref.track_id,
				  EXISTS (
				    SELECT 1
				    FROM votes vote
				    WHERE vote.track_id = provider_ref.track_id
				      AND vote.user_id = :userId
				      AND vote.voted_on = :votedOn
				  ) AS has_voted_today
				FROM track_provider_refs provider_ref
				WHERE provider_ref.provider = :provider
				  AND provider_ref.external_track_id IN (:externalTrackIds)
				""")
			.param("provider", provider.name())
			.param("externalTrackIds", externalTrackIds)
			.param("userId", userId)
			.param("votedOn", votedOn)
			.query((row, rowNumber) -> Map.entry(
				row.getString("external_track_id"),
				new Registration(
					row.getObject("track_id", UUID.class),
					row.getBoolean("has_voted_today"))))
			.list()
			.forEach(entry -> registrations.put(entry.getKey(), entry.getValue()));
		return Map.copyOf(registrations);
	}
}
