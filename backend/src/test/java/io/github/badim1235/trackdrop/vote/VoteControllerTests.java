package io.github.badim1235.trackdrop.vote;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.badim1235.trackdrop.TestcontainersConfiguration;
import io.github.badim1235.trackdrop.catalog.MusicCatalogLookupService;
import io.github.badim1235.trackdrop.catalog.MusicCatalogTrack;
import io.github.badim1235.trackdrop.catalog.MusicProvider;
import io.github.badim1235.trackdrop.identity.SupabaseAuthGateway;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
class VoteControllerTests {
	private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();
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
	void votesForAnExistingTrackAndConsumesOneQuota() throws Exception {
		UUID trackId = createTrack(signUp(), uniqueExternalId(), "Existing Track");
		Cookie voter = signUp();

		mockMvc.perform(post("/api/v1/tracks/{trackId}/votes", trackId)
				.cookie(voter)
				.with(csrf()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.vote.trackId").value(trackId.toString()))
			.andExpect(jsonPath("$.todayVoteCount").value(2))
			.andExpect(jsonPath("$.quota.used").value(1))
			.andExpect(jsonPath("$.quota.remaining").value(3));

		mockMvc.perform(get("/api/v1/me").cookie(voter))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.quota.used").value(1));
	}

	@Test
	void rejectsADuplicateVoteWithoutConsumingQuota() throws Exception {
		Cookie recommender = signUp();
		UUID trackId = createTrack(recommender, uniqueExternalId(), "Already Voted Track");

		mockMvc.perform(post("/api/v1/tracks/{trackId}/votes", trackId)
				.cookie(recommender)
				.with(csrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("ALREADY_VOTED"))
			.andExpect(jsonPath("$.error.details.trackId").value(trackId.toString()))
			.andExpect(jsonPath("$.error.details.quota.used").value(1));

		mockMvc.perform(get("/api/v1/me").cookie(recommender))
			.andExpect(jsonPath("$.quota.used").value(1));
	}

	@Test
	void rejectsAMissingTrackWithoutConsumingQuota() throws Exception {
		Cookie voter = signUp();

		mockMvc.perform(post("/api/v1/tracks/{trackId}/votes", UUID.randomUUID())
				.cookie(voter)
				.with(csrf()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("TRACK_NOT_FOUND"));

		mockMvc.perform(get("/api/v1/me").cookie(voter))
			.andExpect(jsonPath("$.quota.used").value(0));
	}

	@Test
	void preventsADetailVoteWhileTheTrackIsCoolingDown() throws Exception {
		UUID trackId = createTrack(signUp(), uniqueExternalId(), "Cooling Track");
		LocalDate today = LocalDate.now(SERVICE_ZONE);
		moveRecommendationCycle(trackId, today.minusDays(1));
		Cookie voter = signUp();

		mockMvc.perform(post("/api/v1/tracks/{trackId}/votes", trackId)
				.cookie(voter).with(csrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RECOMMENDATION_COOLDOWN"))
			.andExpect(jsonPath("$.error.details.recommendationAvailableOn")
				.value(today.plusDays(2).toString()));
	}

	@Test
	void requiresANewRecommendationCycleAfterTheCooldown() throws Exception {
		UUID trackId = createTrack(signUp(), uniqueExternalId(), "Eligible Track");
		moveRecommendationCycle(trackId, LocalDate.now(SERVICE_ZONE).minusDays(3));
		Cookie voter = signUp();

		mockMvc.perform(post("/api/v1/tracks/{trackId}/votes", trackId)
				.cookie(voter).with(csrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RECOMMENDATION_REQUIRED"));
	}

	@Test
	void rollsBackTheFifthVoteWhenDailyQuotaIsExhausted() throws Exception {
		Cookie firstOwner = signUp();
		Cookie secondOwner = signUp();
		List<UUID> trackIds = new ArrayList<>();
		for (int index = 1; index <= 4; index++) {
			trackIds.add(createTrack(
				firstOwner, uniqueExternalId(), "Quota Track " + index));
		}
		trackIds.add(createTrack(secondOwner, uniqueExternalId(), "Quota Track 5"));
		Cookie voter = signUp();

		for (int index = 0; index < 4; index++) {
			mockMvc.perform(post("/api/v1/tracks/{trackId}/votes", trackIds.get(index))
					.cookie(voter)
					.with(csrf()))
				.andExpect(status().isCreated());
		}

		UUID fifthTrackId = trackIds.get(4);
		mockMvc.perform(post("/api/v1/tracks/{trackId}/votes", fifthTrackId)
				.cookie(voter)
				.with(csrf()))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.error.code").value("DAILY_LIMIT_EXCEEDED"))
			.andExpect(jsonPath("$.error.details.quota.remaining").value(0));

		long fifthTrackVotes = jdbcClient.sql("SELECT COUNT(*) FROM votes WHERE track_id = :trackId")
			.param("trackId", fifthTrackId)
			.query(Long.class)
			.single();
		org.assertj.core.api.Assertions.assertThat(fifthTrackVotes).isEqualTo(1);
	}

	private UUID createTrack(Cookie owner, String externalTrackId, String title) throws Exception {
		stubTrack(externalTrackId, title);
		MvcResult result = mockMvc.perform(post("/api/v1/recommendations")
				.cookie(owner)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"provider":"APPLE_MUSIC","externalTrackId":"%s","comment":"좋은 음악을 소개합니다."}
					""".formatted(externalTrackId)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.track.id"));
	}

	private Cookie signUp() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		int address = IP_SEQUENCE.incrementAndGet();
		String remoteAddress = "10.20.%d.%d".formatted(
			(address / 250) % 250 + 1,
			address % 250 + 1);
		String email = suffix + "@example.com";
		when(supabaseAuth.signIn(email, "chatgpt5555"))
			.thenReturn(new SupabaseAuthGateway.AuthenticatedUser(
				UUID.randomUUID(), email, Instant.now()));
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
				.with(csrf())
				.with(request -> {
					request.setRemoteAddr(remoteAddress);
					return request;
				})
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
				"테스트 아티스트",
				"테스트 앨범",
				"https://example.com/cover.jpg",
				2026,
				null,
				false,
				"Rock",
				"https://example.com/preview.m4a",
				"https://music.apple.com/kr/song/" + externalTrackId)));
	}

	private void moveRecommendationCycle(UUID trackId, LocalDate date) {
		jdbcClient.sql("UPDATE recommendations SET recommended_on = :date WHERE track_id = :trackId")
			.param("date", date)
			.param("trackId", trackId)
			.update();
		jdbcClient.sql("UPDATE votes SET voted_on = :date WHERE track_id = :trackId")
			.param("date", date)
			.param("trackId", trackId)
			.update();
	}

	private static String uniqueExternalId() {
		return UUID.randomUUID().toString();
	}
}
