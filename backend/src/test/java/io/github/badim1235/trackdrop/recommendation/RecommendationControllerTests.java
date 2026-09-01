package io.github.badim1235.trackdrop.recommendation;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RecommendationControllerTests {
	private static final String ROCK_GENRE_ID = "10000000-0000-0000-0000-000000000020";

	@Autowired
	private MockMvc mockMvc;

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
	}

	@Test
	void rejectsDuplicateTrackWithoutConsumingAnotherQuota() throws Exception {
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
			.andExpect(jsonPath("$.error.code").value("ALREADY_RECOMMENDED"))
			.andExpect(jsonPath("$.error.details.quotaConsumed").value(false));

		mockMvc.perform(get("/api/v1/me").cookie(session))
			.andExpect(jsonPath("$.quota.used").value(1));
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
				"Rock",
				"https://example.com/preview.m4a",
				"https://music.apple.com/kr/song/" + externalTrackId)));
	}

	private static String request(String externalTrackId, String comment) {
		return """
			{"provider":"APPLE_MUSIC","externalTrackId":"%s","primaryGenreId":"%s","comment":"%s"}
			""".formatted(externalTrackId, ROCK_GENRE_ID, comment);
	}
}
