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
import io.github.badim1235.trackdrop.ranking.DailyRankingService;
import io.github.badim1235.trackdrop.shared.quota.DailyQuotaService;
import io.github.badim1235.trackdrop.shared.quota.DailyQuotaSnapshot;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DailyChartService {
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
	private static final int LIVE_PAGE_SIZE = 20;
	private static final int FINAL_FIRST_PAGE_SIZE = 20;
	private static final int FINAL_REMAINDER_PAGE_SIZE = 30;
	private static final int FINAL_CHART_SIZE = 50;
	private static final UUID EMPTY_UUID = new UUID(0, 0);
	private static final RowMapper<Item> ITEM_ROW_MAPPER = DailyChartService::mapItem;
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
			JOIN LATERAL (
				SELECT latest.recommender_user_id, latest.primary_genre_id,
					latest.comment, latest.comment_visibility
				FROM recommendations latest
				WHERE latest.track_id = track.id
				  AND latest.recommended_on <= :chartDate
				ORDER BY latest.recommended_on DESC, latest.created_at DESC, latest.id DESC
				LIMIT 1
			) recommendation ON TRUE
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
	private static final String FINAL_CHART_SQL = """
		SELECT
			daily_ranking.rank,
			daily_ranking.vote_count,
			FALSE AS has_voted_today,
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
		FROM daily_rankings daily_ranking
		JOIN tracks track ON track.id = daily_ranking.track_id
		JOIN LATERAL (
			SELECT latest.recommender_user_id, latest.primary_genre_id,
				latest.comment, latest.comment_visibility
			FROM recommendations latest
			WHERE latest.track_id = track.id
			  AND latest.recommended_on <= :chartDate
			ORDER BY latest.recommended_on DESC, latest.created_at DESC, latest.id DESC
			LIMIT 1
		) recommendation ON TRUE
		JOIN users recommender ON recommender.id = recommendation.recommender_user_id
		JOIN genres genre ON genre.id = recommendation.primary_genre_id
		JOIN track_provider_refs provider_ref
		  ON provider_ref.track_id = track.id AND provider_ref.provider = 'APPLE_MUSIC'
		WHERE daily_ranking.ranking_run_id = :runId
		  AND daily_ranking.ranking_date = :chartDate
		  AND daily_ranking.scope_type = :scopeType
		  AND (:allGenres = TRUE OR daily_ranking.genre_id = :genreId)
		  AND daily_ranking.rank > :afterRank
		  AND daily_ranking.rank <= :finalChartSize
		ORDER BY daily_ranking.rank
		LIMIT :queryLimit
		""";

	private final JdbcClient jdbcClient;
	private final DailyQuotaService quotaService;
	private final DailyRankingService rankingService;
	private final Clock clock;

	DailyChartService(
		JdbcClient jdbcClient,
		DailyQuotaService quotaService,
		DailyRankingService rankingService,
		Clock clock
	) {
		this.jdbcClient = jdbcClient;
		this.quotaService = quotaService;
		this.rankingService = rankingService;
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

		boolean finalChart = chartDate.isBefore(today);
		String genreCode = normalizeGenre(requestedGenre);
		Optional<Genre> genre = "all".equals(genreCode)
			? Optional.empty()
			: findGenre(genreCode, finalChart);
		if (!"all".equals(genreCode) && genre.isEmpty()) {
			throw DailyChartException.genreNotFound();
		}

		return finalChart
			? getFinal(chartDate, genreCode, genre, cursor, now)
			: getLive(chartDate, genreCode, genre, cursor, viewerId, now);
	}

	private DailyChartResponse getLive(
		LocalDate chartDate,
		String genreCode,
		Optional<Genre> genre,
		String cursor,
		UUID viewerId,
		Instant now
	) {
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
			.param("queryLimit", LIVE_PAGE_SIZE + 1)
			.query(ITEM_ROW_MAPPER)
			.list();

		DailyQuotaSnapshot quota = viewerId == null ? null : quotaService.current(viewerId);
		return response(
			chartDate, Status.LIVE, genre, cursorState, queriedItems, LIVE_PAGE_SIZE, quota, true);
	}

	private DailyChartResponse getFinal(
		LocalDate chartDate,
		String genreCode,
		Optional<Genre> genre,
		String cursor,
		Instant now
	) {
		rankingService.snapshot(chartDate);
		FinalizedRun run = findFinalizedRun(chartDate).orElseThrow(DailyChartException::rankingNotAvailable);
		CursorState cursorState = cursor == null || cursor.isBlank()
			? new CursorState(chartDate, genreCode, run.completedAt(), 0)
			: decodeCursor(cursor, chartDate, genreCode, now);
		if (cursorState.asOf().toEpochMilli() != run.completedAt().toEpochMilli()) {
			throw DailyChartException.invalidCursor(new IllegalArgumentException("Snapshot cursor is stale"));
		}

		UUID selectedGenreId = genre.map(Genre::id).orElse(EMPTY_UUID);
		int pageSize = cursorState.afterRank() == 0
			? FINAL_FIRST_PAGE_SIZE
			: FINAL_REMAINDER_PAGE_SIZE;
		List<Item> queriedItems = jdbcClient.sql(FINAL_CHART_SQL)
			.param("runId", run.id())
			.param("chartDate", chartDate)
			.param("scopeType", genre.isEmpty() ? "ALL" : "GENRE")
			.param("allGenres", genre.isEmpty())
			.param("genreId", selectedGenreId)
			.param("afterRank", cursorState.afterRank())
			.param("finalChartSize", FINAL_CHART_SIZE)
			.param("queryLimit", pageSize + 1)
			.query(ITEM_ROW_MAPPER)
			.list();

		return response(chartDate, Status.FINAL, genre, cursorState, queriedItems, pageSize, null, false);
	}

	private static DailyChartResponse response(
		LocalDate chartDate,
		Status status,
		Optional<Genre> genre,
		CursorState cursorState,
		List<Item> queriedItems,
		int pageSize,
		DailyQuotaSnapshot quota,
		boolean canVote
	) {
		boolean hasMore = queriedItems.size() > pageSize;
		List<Item> items = hasMore ? List.copyOf(queriedItems.subList(0, pageSize)) : queriedItems;
		String nextCursor = hasMore
			? encodeCursor(new CursorState(chartDate, cursorState.genreCode(), cursorState.asOf(), items.getLast().rank()))
			: null;

		return new DailyChartResponse(
			chartDate,
			status,
			new Scope(genre.isPresent() ? "GENRE" : "ALL", genre.orElse(null)),
			cursorState.asOf(),
			items,
			new Page(pageSize, hasMore, nextCursor),
			quota,
			new Actions(canVote));
	}

	private Optional<FinalizedRun> findFinalizedRun(LocalDate rankingDate) {
		return jdbcClient.sql("""
				SELECT id, completed_at
				FROM ranking_runs
				WHERE ranking_date = :rankingDate
				  AND status = 'COMPLETED'
				""")
			.param("rankingDate", rankingDate)
			.query((row, rowNumber) -> new FinalizedRun(
				row.getObject("id", UUID.class),
				row.getObject("completed_at", OffsetDateTime.class).toInstant()))
			.optional();
	}

	private Optional<Genre> findGenre(String code, boolean includeInactive) {
		return jdbcClient.sql("""
				SELECT id, code, display_name, sort_order
				FROM genres
				WHERE code = :code
				  AND (:includeInactive = TRUE OR active = TRUE)
				""")
			.param("code", code)
			.param("includeInactive", includeInactive)
			.query((row, rowNumber) -> new Genre(
				row.getObject("id", UUID.class),
				row.getString("code"),
				row.getString("display_name"),
				row.getInt("sort_order")))
			.optional();
	}

	private static Item mapItem(ResultSet row, int rowNumber) throws SQLException {
		String previewUrl = row.getString("preview_url");
		return new Item(
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
				new Preview(previewUrl != null, MusicProvider.APPLE_MUSIC, previewUrl),
				row.getString("external_url")));
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

	private record FinalizedRun(UUID id, Instant completedAt) {
	}
}
