package io.github.badim1235.trackdrop.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.badim1235.trackdrop.TestcontainersConfiguration;
import io.github.badim1235.trackdrop.catalog.MusicCatalogLookupService;
import io.github.badim1235.trackdrop.catalog.MusicCatalogTrack;
import io.github.badim1235.trackdrop.catalog.MusicProvider;
import io.github.badim1235.trackdrop.identity.SupabaseAuthGateway;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RecommendationControllerTests {
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@MockitoBean
	private MusicCatalogLookupService catalogLookup;

	@MockitoBean
	private SupabaseAuthGateway supabaseAuth;

	@Test
	void createsRecommendationFirstVoteAndConsumesOneQuotaTogether() throws Exception {
		Cookie session = signUp();
		stubTrack("1828393595", "0+0");

		mockMvc.perform(get("/api/v1/genres"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[19].displayName").value("Rock"));

		mockMvc.perform(post("/api/v1/recommendations")
				.cookie(session)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("1828393595", "잔잔하게 번지는 기타가 좋아요.")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.track.title").value("0+0"))
			.andExpect(jsonPath("$.track.primaryGenreName").value("Rock"))
			.andExpect(jsonPath("$.recommendation.primaryGenre.displayName").value("Rock"))
			.andExpect(jsonPath("$.vote.created").value(true))
			.andExpect(jsonPath("$.quota.used").value(1))
			.andExpect(jsonPath("$.quota.remaining").value(3));

		mockMvc.perform(get("/api/v1/me").cookie(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.quota.used").value(1));

		String genreSource = jdbcClient.sql("""
				SELECT track_genre.source
				FROM track_genres track_genre
				JOIN track_provider_refs provider_ref ON provider_ref.track_id = track_genre.track_id
				WHERE provider_ref.provider = 'APPLE_MUSIC'
				  AND provider_ref.external_track_id = '1828393595'
				""")
			.query(String.class)
			.single();
		assertThat(genreSource).isEqualTo("PROVIDER");
	}

	@Test
	void fallsBackToOtherWhenAppleGenreIsNotInTheActiveGenreList() throws Exception {
		Cookie session = signUp();
		stubTrack("4828393595", "Unmapped Track", "Vocal");

		mockMvc.perform(post("/api/v1/recommendations")
				.cookie(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("4828393595", "분류되지 않은 장르도 추천합니다.")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.track.primaryGenreName").value("Vocal"))
			.andExpect(jsonPath("$.recommendation.primaryGenre.code").value("other"));
	}

	@Test
	void rejectsAnotherRecommendationForATrackInTheCurrentChart() throws Exception {
		Cookie session = signUp();
		stubTrack("2828393595", "Duplicate Track");

		mockMvc.perform(post("/api/v1/recommendations")
				.cookie(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("2828393595", "첫 번째 한줄평입니다.")))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/recommendations")
				.cookie(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("2828393595", "두 번째 한줄평입니다.")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("ALREADY_IN_CURRENT_CHART"))
			.andExpect(jsonPath("$.error.details.quotaConsumed").value(false));

		mockMvc.perform(get("/api/v1/me").cookie(session))
			.andExpect(jsonPath("$.quota.used").value(1));
	}

	@Test
	void rejectsTrackDuringTheThreeDayCooldownWithoutConsumingAnotherQuota() throws Exception {
		Cookie session = signUp();
		String externalTrackId = "3828393595";
		stubTrack(externalTrackId, "Cooling Track");

		MvcResult first = mockMvc.perform(post("/api/v1/recommendations")
				.cookie(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(externalTrackId, "첫 번째 한줄평입니다.")))
			.andExpect(status().isCreated())
			.andReturn();
		String trackId = com.jayway.jsonpath.JsonPath.read(
			first.getResponse().getContentAsString(), "$.track.id");
		LocalDate today = LocalDate.now(SERVICE_ZONE);
		jdbcClient.sql("UPDATE votes SET voted_on = :past WHERE track_id = :trackId")
			.param("past", today.minusDays(1))
			.param("trackId", UUID.fromString(trackId))
			.update();

		mockMvc.perform(post("/api/v1/recommendations")
				.cookie(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(externalTrackId, "두 번째 한줄평입니다.")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RECOMMENDATION_COOLDOWN"))
			.andExpect(jsonPath("$.error.details.recommendationAvailableOn")
				.value(today.plusDays(3).toString()))
			.andExpect(jsonPath("$.error.details.quotaConsumed").value(false));

		mockMvc.perform(get("/api/v1/me").cookie(session))
			.andExpect(jsonPath("$.quota.used").value(1));
	}

	@Test
	void allowsTheSameTrackAgainOnTheThirdDayAndReusesItsTrackRecord() throws Exception {
		Cookie firstRecommender = signUp();
		Cookie nextRecommender = signUp();
		String externalTrackId = "5828393595";
		stubTrack(externalTrackId, "Returning Track");

		MvcResult first = mockMvc.perform(post("/api/v1/recommendations")
				.cookie(firstRecommender).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(externalTrackId, "첫 번째 한줄평입니다.")))
			.andExpect(status().isCreated())
			.andReturn();
		String trackId = com.jayway.jsonpath.JsonPath.read(
			first.getResponse().getContentAsString(), "$.track.id");
		LocalDate today = LocalDate.now(SERVICE_ZONE);
		jdbcClient.sql("UPDATE recommendations SET recommended_on = :past WHERE track_id = :trackId")
			.param("past", today.minusDays(3))
			.param("trackId", UUID.fromString(trackId))
			.update();
		jdbcClient.sql("UPDATE votes SET voted_on = :past WHERE track_id = :trackId")
			.param("past", today.minusDays(3))
			.param("trackId", UUID.fromString(trackId))
			.update();

		mockMvc.perform(post("/api/v1/recommendations")
				.cookie(nextRecommender).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(externalTrackId, "사흘 뒤 다시 추천합니다.")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.track.id").value(trackId))
			.andExpect(jsonPath("$.recommendation.comment").value("사흘 뒤 다시 추천합니다."));

		assertThat(jdbcClient.sql("SELECT COUNT(*) FROM tracks WHERE id = :trackId")
			.param("trackId", UUID.fromString(trackId))
			.query(Integer.class)
			.single()).isEqualTo(1);
		assertThat(jdbcClient.sql("SELECT COUNT(*) FROM recommendations WHERE track_id = :trackId")
			.param("trackId", UUID.fromString(trackId))
			.query(Integer.class)
			.single()).isEqualTo(2);
	}

	@Test
	void allowsOnlyFourSuccessfulRecommendationsPerDay() throws Exception {
		Cookie session = signUp();
		for (int index = 1; index <= 5; index++) {
			stubTrack("300" + index, "Track " + index);
		}

		for (int index = 1; index <= 4; index++) {
			mockMvc.perform(post("/api/v1/recommendations")
					.cookie(session).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(request("300" + index, "추천 한줄평 " + index)))
				.andExpect(status().isCreated());
		}

		mockMvc.perform(post("/api/v1/recommendations")
				.cookie(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("3005", "다섯 번째 추천입니다.")))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.error.code").value("DAILY_LIMIT_EXCEEDED"))
			.andExpect(jsonPath("$.error.details.quota.remaining").value(0));
	}

	private Cookie signUp() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String email = suffix + "@example.com";
		when(supabaseAuth.signIn(email, "chatgpt5555"))
			.thenReturn(new SupabaseAuthGateway.AuthenticatedUser(
				UUID.randomUUID(), email, Instant.now()));
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"chatgpt5555","rememberMe":false}
					""".formatted(email)))
			.andExpect(status().isOk())
			.andReturn();
		return result.getResponse().getCookie("TRACKDROP_SESSION");
	}

	private void stubTrack(String externalTrackId, String title) {
		stubTrack(externalTrackId, title, "Rock");
	}

	private void stubTrack(String externalTrackId, String title, String genreName) {
		when(catalogLookup.lookup(MusicProvider.APPLE_MUSIC, externalTrackId))
			.thenReturn(Optional.of(new MusicCatalogTrack(
				externalTrackId,
				title,
				"한로로",
				"이상비행",
				"https://example.com/cover.jpg",
				2025,
				null,
				false,
				genreName,
				"https://example.com/preview.m4a",
				"https://music.apple.com/kr/song/" + externalTrackId)));
	}

	private static String request(String externalTrackId, String comment) {
		return """
			{"provider":"APPLE_MUSIC","externalTrackId":"%s","comment":"%s"}
			""".formatted(externalTrackId, comment);
	}
}
