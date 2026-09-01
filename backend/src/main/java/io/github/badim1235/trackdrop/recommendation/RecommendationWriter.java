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
		UUID primaryGenreId,
		String comment
	) {
		Genre genre = activeGenre(primaryGenreId)
			.orElseThrow(RecommendationException::genreNotFound);
		findExisting(provider, catalogTrack.externalTrackId())
			.ifPresent(existing -> {
				throw RecommendationException.alreadyRecommended(
					existing.trackId(), existing.recommendationId());
			});

		Instant now = clock.instant();
		OffsetDateTime timestamp = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
		LocalDate today = LocalDate.ofInstant(now, SERVICE_ZONE);
		UUID trackId = UUID.randomUUID();
		UUID recommendationId = UUID.randomUUID();

		insertTrack(trackId, catalogTrack, timestamp);
		int providerRefCreated = insertProviderRef(trackId, provider, catalogTrack, timestamp);
		if (providerRefCreated == 0) {
			ExistingRecommendation existing = findExisting(provider, catalogTrack.externalTrackId())
				.orElseThrow(IllegalStateException::new);
			throw RecommendationException.alreadyRecommended(
				existing.trackId(), existing.recommendationId());
		}

		DailyQuotaSnapshot quota = quotaService.consume(userId);
		jdbcClient.sql("""
				INSERT INTO track_genres (track_id, genre_id, source, created_at)
				VALUES (:trackId, :genreId, 'USER_SELECTED', :now)
				""")
			.param("trackId", trackId)
			.param("genreId", primaryGenreId)
			.param("now", timestamp)
			.update();
		jdbcClient.sql("""
				INSERT INTO recommendations (
					id, recommender_user_id, track_id, primary_genre_id,
					comment, comment_visibility, created_at
				)
				VALUES (
					:id, :userId, :trackId, :genreId,
					:comment, 'VISIBLE', :now
				)
				""")
			.param("id", recommendationId)
			.param("userId", userId)
			.param("trackId", trackId)
			.param("genreId", primaryGenreId)
			.param("comment", comment)
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

	private Optional<Genre> activeGenre(UUID genreId) {
		return jdbcClient.sql("""
				SELECT id, code, display_name, sort_order
				FROM genres
				WHERE id = :id AND active = TRUE
				""")
			.param("id", genreId)
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
				SELECT provider_ref.track_id, recommendation.id AS recommendation_id
				FROM track_provider_refs provider_ref
				LEFT JOIN recommendations recommendation
				  ON recommendation.track_id = provider_ref.track_id
				WHERE provider_ref.provider = :provider
				  AND provider_ref.external_track_id = :externalTrackId
				""")
			.param("provider", provider.name())
			.param("externalTrackId", externalTrackId)
			.query((row, rowNumber) -> new ExistingRecommendation(
				row.getObject("track_id", UUID.class),
				row.getObject("recommendation_id", UUID.class)))
			.optional();
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

	private record ExistingRecommendation(UUID trackId, UUID recommendationId) {
	}
}
