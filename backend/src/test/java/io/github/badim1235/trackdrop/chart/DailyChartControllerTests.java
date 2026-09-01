package io.github.badim1235.trackdrop.chart;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.badim1235.trackdrop.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DailyChartControllerTests {
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	void returnsLiveChartRankedByVotesThenCaseInsensitiveTitle() throws Exception {
		List<UUID> users = createUsers(3);
		UUID rock = genreId("rock");
		UUID pop = genreId("pop");
		insertTrack("Beta", "Artist", rock, users.getFirst(), users.subList(0, 2));
		insertTrack("alpha", "Artist", rock, users.getFirst(), users.subList(0, 1));
		insertTrack("Zulu", "Artist", pop, users.getFirst(), users.subList(0, 1));

		mockMvc.perform(get("/api/v1/charts/daily"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("LIVE"))
			.andExpect(jsonPath("$.scope.type").value("ALL"))
			.andExpect(jsonPath("$.items[0].rank").value(1))
			.andExpect(jsonPath("$.items[0].voteCount").value(2))
			.andExpect(jsonPath("$.items[0].track.title").value("Beta"))
			.andExpect(jsonPath("$.items[1].track.title").value("alpha"))
			.andExpect(jsonPath("$.items[2].track.title").value("Zulu"));

		mockMvc.perform(get("/api/v1/charts/daily").param("genre", "rock"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scope.type").value("GENRE"))
			.andExpect(jsonPath("$.scope.genre.code").value("rock"))
			.andExpect(jsonPath("$.items.length()").value(2));
	}

	@Test
	void returnsTwentyTracksThenContinuesFromOpaqueCursor() throws Exception {
		UUID user = createUsers(1).getFirst();
		UUID rock = genreId("rock");
		for (int index = 1; index <= 21; index++) {
			insertTrack("Track %02d".formatted(index), "Artist", rock, user, List.of(user));
		}

		MvcResult firstPage = mockMvc.perform(get("/api/v1/charts/daily"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(20))
			.andExpect(jsonPath("$.items[0].track.title").value("Track 01"))
			.andExpect(jsonPath("$.page.hasMore").value(true))
			.andReturn();
		String responseBody = firstPage.getResponse().getContentAsString(StandardCharsets.UTF_8);
		Matcher cursorMatch = Pattern.compile("\\\"nextCursor\\\":\\\"([^\\\"]+)\\\"").matcher(responseBody);
		if (!cursorMatch.find()) {
			throw new AssertionError("Expected the first chart page to include a cursor");
		}
		String cursor = cursorMatch.group(1);

		mockMvc.perform(get("/api/v1/charts/daily").param("cursor", cursor))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].rank").value(21))
			.andExpect(jsonPath("$.items[0].track.title").value("Track 21"))
			.andExpect(jsonPath("$.page.hasMore").value(false));
	}

	@Test
	void rejectsUnknownGenre() throws Exception {
		mockMvc.perform(get("/api/v1/charts/daily").param("genre", "missing"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("GENRE_NOT_FOUND"));
	}

	private List<UUID> createUsers(int count) {
		List<UUID> users = new ArrayList<>();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2);
		for (int index = 0; index < count; index++) {
			UUID id = UUID.randomUUID();
			String suffix = id.toString().substring(0, 8);
			jdbcClient.sql("""
					INSERT INTO users (
						id, email, email_normalized, email_verified_at,
						public_nickname, status, created_at, updated_at
					)
					VALUES (
						:id, :email, :email, :now,
						:nickname, 'ACTIVE', :now, :now
					)
					""")
				.param("id", id)
				.param("email", "chart-" + suffix + "@example.com")
				.param("nickname", "차트테스트" + suffix)
				.param("now", now)
				.update();
			users.add(id);
		}
		return users;
	}

	private UUID genreId(String code) {
		return jdbcClient.sql("SELECT id FROM genres WHERE code = :code")
			.param("code", code)
			.query(UUID.class)
			.single();
	}

	private UUID insertTrack(
		String title,
		String artist,
		UUID genreId,
		UUID recommenderId,
		List<UUID> voterIds
	) {
		UUID trackId = UUID.randomUUID();
		UUID recommendationId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
		jdbcClient.sql("""
				INSERT INTO tracks (
					id, title, artist_name, album_name, album_cover_url,
					release_year, explicit, provider_genre_name, created_at, updated_at
				)
				VALUES (
					:id, :title, :artist, 'Test Album', NULL,
					2026, FALSE, 'Rock', :now, :now
				)
				""")
			.param("id", trackId)
			.param("title", title)
			.param("artist", artist)
			.param("now", now)
			.update();
		jdbcClient.sql("""
				INSERT INTO track_provider_refs (
					id, track_id, provider, external_track_id,
					external_url, preview_url, metadata_refreshed_at
				)
				VALUES (
					:id, :trackId, 'APPLE_MUSIC', :externalTrackId,
					NULL, NULL, :now
				)
				""")
			.param("id", UUID.randomUUID())
			.param("trackId", trackId)
			.param("externalTrackId", trackId.toString())
			.param("now", now)
			.update();
		jdbcClient.sql("""
				INSERT INTO track_genres (track_id, genre_id, source, created_at)
				VALUES (:trackId, :genreId, 'USER_SELECTED', :now)
				""")
			.param("trackId", trackId)
			.param("genreId", genreId)
			.param("now", now)
			.update();
		jdbcClient.sql("""
				INSERT INTO recommendations (
					id, recommender_user_id, track_id, primary_genre_id,
					comment, comment_visibility, created_at
				)
				VALUES (
					:id, :userId, :trackId, :genreId,
					'테스트 한줄평', 'VISIBLE', :now
				)
				""")
			.param("id", recommendationId)
			.param("userId", recommenderId)
			.param("trackId", trackId)
			.param("genreId", genreId)
			.param("now", now)
			.update();
		LocalDate today = LocalDate.now(SERVICE_ZONE);
		for (UUID voterId : voterIds) {
			jdbcClient.sql("""
					INSERT INTO votes (id, user_id, track_id, voted_on, created_at)
					VALUES (:id, :userId, :trackId, :today, :now)
					""")
				.param("id", UUID.randomUUID())
				.param("userId", voterId)
				.param("trackId", trackId)
				.param("today", today)
				.param("now", now)
				.update();
		}
		return trackId;
	}
}
