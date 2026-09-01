package io.github.badim1235.trackdrop.chart;

import io.github.badim1235.trackdrop.catalog.GenreResponse.Genre;
import io.github.badim1235.trackdrop.catalog.MusicProvider;
import io.github.badim1235.trackdrop.chart.DailyChartResponse.Actions;
import io.github.badim1235.trackdrop.chart.DailyChartResponse.Item;
import io.github.badim1235.trackdrop.chart.DailyChartResponse.Page;
import io.github.badim1235.trackdrop.chart.DailyChartResponse.Preview;
import io.github.badim1235.trackdrop.chart.DailyChartResponse.Scope;
import io.github.badim1235.trackdrop.chart.DailyChartResponse.Status;
import io.github.badim1235.trackdrop.chart.DailyChartResponse.Track;
import io.github.badim1235.trackdrop.shared.quota.DailyQuotaService;
import io.github.badim1235.trackdrop.shared.quota.DailyQuotaSnapshot;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DailyChartService {
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
	private static final int PAGE_SIZE = 20;
	private static final UUID EMPTY_UUID = new UUID(0, 0);
	private static final String LIVE_CHART_SQL = """
		WITH ranked AS (
			SELECT
				ROW_NUMBER() OVER (
					ORDER BY
						COUNT(vote.id) DESC,
						track.title COLLATE trackdrop_nocase ASC,
						track.artist_name COLLATE trackdrop_nocase ASC,
						track.id ASC
				) AS rank,
				COUNT(vote.id)::INTEGER AS vote_count,
				track.id,
				track.title,
				track.artist_name,
				track.album_name,
				track.album_cover_url,
				track.release_year,
				track.explicit,
				genre.id AS genre_id,
				genre.code AS genre_code,
				genre.display_name AS genre_display_name,
				genre.sort_order AS genre_sort_order,
				CASE WHEN recommendation.comment_visibility = 'VISIBLE' THEN recommendation.comment END AS comment,
				CASE WHEN recommendation.comment_visibility = 'VISIBLE' THEN recommender.public_nickname END AS recommender_nickname,
				provider_ref.preview_url,
				provider_ref.external_url
			FROM votes vote
			JOIN tracks track ON track.id = vote.track_id
			JOIN recommendations recommendation ON recommendation.track_id = track.id
			JOIN users recommender ON recommender.id = recommendation.recommender_user_id
			JOIN genres genre ON genre.id = recommendation.primary_genre_id
			JOIN track_provider_refs provider_ref
			  ON provider_ref.track_id = track.id AND provider_ref.provider = 'APPLE_MUSIC'
			WHERE vote.voted_on = :chartDate
			  AND vote.created_at <= :asOf
			  AND (:allGenres = TRUE OR recommendation.primary_genre_id = :genreId)
			GROUP BY
				track.id,
				genre.id,
				recommendation.comment,
				recommendation.comment_visibility,
				recommender.public_nickname,
				provider_ref.preview_url,
				provider_ref.external_url
		)
		SELECT ranked.*,
			EXISTS (
				SELECT 1
				FROM votes viewer_vote
				WHERE viewer_vote.user_id = :viewerId
				  AND viewer_vote.track_id = ranked.id
				  AND viewer_vote.voted_on = :chartDate
			) AS has_voted_today
		FROM ranked
		WHERE ranked.rank > :afterRank
		ORDER BY ranked.rank
		LIMIT :queryLimit
		""";

	private final JdbcClient jdbcClient;
	private final DailyQuotaService quotaService;
	private final Clock clock;

	DailyChartService(JdbcClient jdbcClient, DailyQuotaService quotaService, Clock clock) {
		this.jdbcClient = jdbcClient;
		this.quotaService = quotaService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	DailyChartResponse get(LocalDate requestedDate, String requestedGenre, String cursor, UUID viewerId) {
		Instant now = clock.instant();
		LocalDate today = LocalDate.ofInstant(now, SERVICE_ZONE);
		LocalDate chartDate = requestedDate == null ? today : requestedDate;
		if (chartDate.isAfter(today)) {
			throw DailyChartException.futureDate();
		}
		if (chartDate.isBefore(today)) {
			throw DailyChartException.rankingNotAvailable();
		}

		String genreCode = normalizeGenre(requestedGenre);
		Optional<Genre> genre = "all".equals(genreCode) ? Optional.empty() : activeGenre(genreCode);
		if (!"all".equals(genreCode) && genre.isEmpty()) {
			throw DailyChartException.genreNotFound();
		}

		CursorState cursorState = cursor == null || cursor.isBlank()
			? new CursorState(chartDate, genreCode, now, 0)
			: decodeCursor(cursor, chartDate, genreCode, now);
		UUID selectedGenreId = genre.map(Genre::id).orElse(EMPTY_UUID);
		UUID selectedViewerId = viewerId == null ? EMPTY_UUID : viewerId;

		List<Item> queriedItems = jdbcClient.sql(LIVE_CHART_SQL)
			.param("chartDate", chartDate)
			.param("asOf", OffsetDateTime.ofInstant(cursorState.asOf(), ZoneOffset.UTC))
			.param("allGenres", genre.isEmpty())
			.param("genreId", selectedGenreId)
			.param("viewerId", selectedViewerId)
			.param("afterRank", cursorState.afterRank())
			.param("queryLimit", PAGE_SIZE + 1)
			.query((row, rowNumber) -> new Item(
				row.getLong("rank"),
				row.getInt("vote_count"),
				row.getBoolean("has_voted_today"),
				new Track(
					row.getObject("id", UUID.class),
					row.getString("title"),
					row.getString("artist_name"),
					row.getString("album_name"),
					row.getString("album_cover_url"),
					row.getObject("release_year") == null ? null : row.getInt("release_year"),
					row.getBoolean("explicit"),
					new Genre(
						row.getObject("genre_id", UUID.class),
						row.getString("genre_code"),
						row.getString("genre_display_name"),
						row.getInt("genre_sort_order")),
					row.getString("comment"),
					row.getString("recommender_nickname"),
					new Preview(
						row.getString("preview_url") != null,
						MusicProvider.APPLE_MUSIC,
						row.getString("preview_url")),
					row.getString("external_url"))))
			.list();

		boolean hasMore = queriedItems.size() > PAGE_SIZE;
		List<Item> items = hasMore ? List.copyOf(queriedItems.subList(0, PAGE_SIZE)) : queriedItems;
		String nextCursor = hasMore
			? encodeCursor(new CursorState(chartDate, genreCode, cursorState.asOf(), items.getLast().rank()))
			: null;
		DailyQuotaSnapshot quota = viewerId == null ? null : quotaService.current(viewerId);

		return new DailyChartResponse(
			chartDate,
			Status.LIVE,
			new Scope(genre.isPresent() ? "GENRE" : "ALL", genre.orElse(null)),
			cursorState.asOf(),
			items,
			new Page(PAGE_SIZE, hasMore, nextCursor),
			quota,
			new Actions(true));
	}

	private Optional<Genre> activeGenre(String code) {
		return jdbcClient.sql("""
				SELECT id, code, display_name, sort_order
				FROM genres
				WHERE code = :code AND active = TRUE
				""")
			.param("code", code)
			.query((row, rowNumber) -> new Genre(
				row.getObject("id", UUID.class),
				row.getString("code"),
				row.getString("display_name"),
				row.getInt("sort_order")))
			.optional();
	}

	private static String normalizeGenre(String genre) {
		return genre == null || genre.isBlank() ? "all" : genre.trim().toLowerCase(Locale.ROOT);
	}

	private static String encodeCursor(CursorState state) {
		String value = String.join("|",
			state.date().toString(),
			state.genreCode(),
			Long.toString(state.asOf().toEpochMilli()),
			Long.toString(state.afterRank()));
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private static CursorState decodeCursor(
		String cursor,
		LocalDate expectedDate,
		String expectedGenre,
		Instant now
	) {
		try {
			String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			String[] parts = value.split("\\|", -1);
			if (parts.length != 4) {
				throw new IllegalArgumentException("Cursor part count");
			}
			CursorState state = new CursorState(
				LocalDate.parse(parts[0]),
				parts[1],
				Instant.ofEpochMilli(Long.parseLong(parts[2])),
				Long.parseLong(parts[3]));
			if (!state.date().equals(expectedDate)
				|| !state.genreCode().equals(expectedGenre)
				|| state.afterRank() < 1
				|| state.asOf().isAfter(now.plusSeconds(1))) {
				throw new IllegalArgumentException("Cursor context mismatch");
			}
			return state;
		} catch (RuntimeException exception) {
			throw DailyChartException.invalidCursor(exception);
		}
	}

	private record CursorState(LocalDate date, String genreCode, Instant asOf, long afterRank) {
	}
}
