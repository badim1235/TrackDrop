package io.github.badim1235.trackdrop.recommendation;

import io.github.badim1235.trackdrop.catalog.GenreResponse.Genre;
import io.github.badim1235.trackdrop.catalog.MusicCatalogTrack;
import io.github.badim1235.trackdrop.catalog.MusicProvider;
import io.github.badim1235.trackdrop.recommendation.RecommendationResponse.Preview;
import io.github.badim1235.trackdrop.recommendation.RecommendationResponse.Recommendation;
import io.github.badim1235.trackdrop.recommendation.RecommendationResponse.Track;
import io.github.badim1235.trackdrop.recommendation.RecommendationResponse.Vote;
import io.github.badim1235.trackdrop.shared.quota.DailyQuotaService;
import io.github.badim1235.trackdrop.shared.quota.DailyQuotaSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class RecommendationWriter {
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	private final JdbcClient jdbcClient;
	private final DailyQuotaService quotaService;
	private final Clock clock;

	RecommendationWriter(
		JdbcClient jdbcClient,
		DailyQuotaService quotaService,
		Clock clock
	) {
		this.jdbcClient = jdbcClient;
		this.quotaService = quotaService;
		this.clock = clock;
	}

	@Transactional
	RecommendationResponse create(
		UUID userId,
		MusicProvider provider,
		MusicCatalogTrack catalogTrack,
		String comment
	) {
		Genre genre = activeProviderGenre(catalogTrack.primaryGenreName())
			.orElseThrow(RecommendationException::providerGenreUnavailable);
		Instant now = clock.instant();
		OffsetDateTime timestamp = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
		LocalDate today = LocalDate.ofInstant(now, SERVICE_ZONE);
		Optional<ExistingRecommendation> existing = findExisting(
			provider, catalogTrack.externalTrackId());
		UUID trackId = existing.map(ExistingRecommendation::trackId).orElseGet(UUID::randomUUID);
		UUID recommendationId = UUID.randomUUID();

		if (existing.isPresent()) {
			ensureRecommendationAvailable(existing.get(), today);
			refreshTrack(trackId, catalogTrack, timestamp);
			refreshProviderRef(trackId, provider, catalogTrack, timestamp);
		} else {
			insertTrack(trackId, catalogTrack, timestamp);
			int providerRefCreated = insertProviderRef(trackId, provider, catalogTrack, timestamp);
			if (providerRefCreated == 0) {
				ExistingRecommendation concurrent = findExisting(provider, catalogTrack.externalTrackId())
					.orElseThrow(IllegalStateException::new);
				ensureRecommendationAvailable(concurrent, today);
				throw RecommendationException.alreadyInCurrentChart(
					concurrent.trackId(), concurrent.recommendationId());
			}
		}

		DailyQuotaSnapshot quota = quotaService.consume(userId);
		jdbcClient.sql("""
				INSERT INTO track_genres (track_id, genre_id, source, created_at)
				VALUES (:trackId, :genreId, 'PROVIDER', :now)
				ON CONFLICT (track_id, genre_id) DO NOTHING
				""")
			.param("trackId", trackId)
			.param("genreId", genre.id())
			.param("now", timestamp)
			.update();
		jdbcClient.sql("""
				INSERT INTO recommendations (
					id, recommender_user_id, track_id, primary_genre_id,
					comment, comment_visibility, recommended_on, created_at
				)
				VALUES (
					:id, :userId, :trackId, :genreId,
					:comment, 'VISIBLE', :today, :now
				)
				""")
			.param("id", recommendationId)
			.param("userId", userId)
			.param("trackId", trackId)
			.param("genreId", genre.id())
			.param("comment", comment)
			.param("today", today)
			.param("now", timestamp)
			.update();
		jdbcClient.sql("""
				INSERT INTO votes (id, user_id, track_id, voted_on, created_at)
				VALUES (:id, :userId, :trackId, :today, :now)
				""")
			.param("id", UUID.randomUUID())
			.param("userId", userId)
			.param("trackId", trackId)
			.param("today", today)
			.param("now", timestamp)
			.update();

		return new RecommendationResponse(
			new Track(
				trackId,
				catalogTrack.title(),
				catalogTrack.artistName(),
				catalogTrack.albumName(),
				catalogTrack.albumCoverUrl(),
				catalogTrack.releaseYear(),
				catalogTrack.explicit(),
				catalogTrack.primaryGenreName(),
				new Preview(
					catalogTrack.previewUrl() != null,
					provider,
					catalogTrack.previewUrl()),
				catalogTrack.externalUrl()),
			new Recommendation(recommendationId, genre, comment, now),
			new Vote(true, today),
			quota);
	}

	private Optional<Genre> activeProviderGenre(String providerGenreName) {
		String normalizedName = providerGenreName == null ? "" : providerGenreName.strip();
		return jdbcClient.sql("""
				SELECT id, code, display_name, sort_order
				FROM genres
				WHERE active = TRUE
				  AND (LOWER(display_name) = LOWER(:providerGenreName) OR code = 'other')
				ORDER BY CASE
				  WHEN LOWER(display_name) = LOWER(:providerGenreName) THEN 0
				  ELSE 1
				END
				LIMIT 1
				""")
			.param("providerGenreName", normalizedName)
			.query((row, rowNumber) -> new Genre(
				row.getObject("id", UUID.class),
				row.getString("code"),
				row.getString("display_name"),
				row.getInt("sort_order")))
			.optional();
	}

	private Optional<ExistingRecommendation> findExisting(
		MusicProvider provider,
		String externalTrackId
	) {
		return jdbcClient.sql("""
				SELECT
					provider_ref.track_id,
					recommendation.id AS recommendation_id,
					recommendation.recommended_on
				FROM track_provider_refs provider_ref
				JOIN tracks track ON track.id = provider_ref.track_id
				JOIN LATERAL (
					SELECT latest.id, latest.recommended_on
					FROM recommendations latest
					WHERE latest.track_id = provider_ref.track_id
					ORDER BY latest.recommended_on DESC, latest.created_at DESC, latest.id DESC
					LIMIT 1
				) recommendation ON TRUE
				WHERE provider_ref.provider = :provider
				  AND provider_ref.external_track_id = :externalTrackId
				FOR UPDATE OF track
				""")
			.param("provider", provider.name())
			.param("externalTrackId", externalTrackId)
			.query((row, rowNumber) -> new ExistingRecommendation(
				row.getObject("track_id", UUID.class),
				row.getObject("recommendation_id", UUID.class),
				row.getObject("recommended_on", LocalDate.class)))
			.optional();
	}

	private void ensureRecommendationAvailable(ExistingRecommendation existing, LocalDate today) {
		if (isInCurrentChart(existing.trackId(), today)) {
			throw RecommendationException.alreadyInCurrentChart(
				existing.trackId(), existing.recommendationId());
		}
		LocalDate availableOn = existing.recommendedOn().plusDays(3);
		if (today.isBefore(availableOn)) {
			throw RecommendationException.recommendationCooldown(
				existing.trackId(), existing.recommendationId(), availableOn);
		}
	}

	private boolean isInCurrentChart(UUID trackId, LocalDate today) {
		return jdbcClient.sql("""
				SELECT EXISTS (
					SELECT 1 FROM votes
					WHERE track_id = :trackId AND voted_on = :today
				)
				""")
			.param("trackId", trackId)
			.param("today", today)
			.query(Boolean.class)
			.single();
	}

	private void refreshTrack(
		UUID trackId,
		MusicCatalogTrack track,
		OffsetDateTime timestamp
	) {
		jdbcClient.sql("""
				UPDATE tracks
				SET title = :title,
					artist_name = :artistName,
					album_name = :albumName,
					album_cover_url = :albumCoverUrl,
					release_year = :releaseYear,
					isrc = :isrc,
					explicit = :explicit,
					provider_genre_name = :providerGenreName,
					updated_at = :now
				WHERE id = :trackId
				""")
			.param("trackId", trackId)
			.param("title", track.title())
			.param("artistName", track.artistName())
			.param("albumName", track.albumName())
			.param("albumCoverUrl", track.albumCoverUrl())
			.param("releaseYear", track.releaseYear())
			.param("isrc", track.isrc())
			.param("explicit", track.explicit())
			.param("providerGenreName", track.primaryGenreName())
			.param("now", timestamp)
			.update();
	}

	private void refreshProviderRef(
		UUID trackId,
		MusicProvider provider,
		MusicCatalogTrack track,
		OffsetDateTime timestamp
	) {
		jdbcClient.sql("""
				UPDATE track_provider_refs
				SET external_url = :externalUrl,
					preview_url = :previewUrl,
					metadata_refreshed_at = :now
				WHERE track_id = :trackId AND provider = :provider
				""")
			.param("trackId", trackId)
			.param("provider", provider.name())
			.param("externalUrl", track.externalUrl())
			.param("previewUrl", track.previewUrl())
			.param("now", timestamp)
			.update();
	}

	private void insertTrack(
		UUID trackId,
		MusicCatalogTrack track,
		OffsetDateTime timestamp
	) {
		jdbcClient.sql("""
				INSERT INTO tracks (
					id, title, artist_name, album_name, album_cover_url,
					release_year, isrc, explicit, provider_genre_name,
					created_at, updated_at
				)
				VALUES (
					:id, :title, :artistName, :albumName, :albumCoverUrl,
					:releaseYear, :isrc, :explicit, :providerGenreName,
					:now, :now
				)
				""")
			.param("id", trackId)
			.param("title", track.title())
			.param("artistName", track.artistName())
			.param("albumName", track.albumName())
			.param("albumCoverUrl", track.albumCoverUrl())
			.param("releaseYear", track.releaseYear())
			.param("isrc", track.isrc())
			.param("explicit", track.explicit())
			.param("providerGenreName", track.primaryGenreName())
			.param("now", timestamp)
			.update();
	}

	private int insertProviderRef(
		UUID trackId,
		MusicProvider provider,
		MusicCatalogTrack track,
		OffsetDateTime timestamp
	) {
		return jdbcClient.sql("""
				INSERT INTO track_provider_refs (
					id, track_id, provider, external_track_id,
					external_url, preview_url, metadata_refreshed_at
				)
				VALUES (
					:id, :trackId, :provider, :externalTrackId,
					:externalUrl, :previewUrl, :now
				)
				ON CONFLICT (provider, external_track_id) DO NOTHING
				""")
			.param("id", UUID.randomUUID())
			.param("trackId", trackId)
			.param("provider", provider.name())
			.param("externalTrackId", track.externalTrackId())
			.param("externalUrl", track.externalUrl())
			.param("previewUrl", track.previewUrl())
			.param("now", timestamp)
			.update();
	}

	private record ExistingRecommendation(
		UUID trackId,
		UUID recommendationId,
		LocalDate recommendedOn
	) {
	}
}
