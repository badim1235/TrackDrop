package io.github.badim1235.trackdrop.identity;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.badim1235.trackdrop.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
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
class AuthControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SupabaseAuthGateway supabaseAuth;

	@MockitoBean
	private SupabaseUserDirectory supabaseUsers;

	@Test
	void signsUpThroughSupabaseAndRequiresEmailVerification() throws Exception {
		String email = uniqueEmail();

		mockMvc.perform(post("/api/v1/auth/sign-up")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"chatgpt5555"}
					""".formatted(email)))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.emailVerificationRequired").value(true));

		verify(supabaseAuth).signUp(email, "chatgpt5555");
	}

	@Test
	void rejectsAnEmailThatAlreadyExistsInSupabaseAuth() throws Exception {
		String email = uniqueEmail();
		when(supabaseUsers.existsByEmail(email)).thenReturn(true);

		mockMvc.perform(post("/api/v1/auth/sign-up")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"chatgpt5555"}
					""".formatted(email)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("EMAIL_TAKEN"))
			.andExpect(jsonPath("$.error.message").value("이미 존재하는 이메일입니다."));

		verifyNoInteractions(supabaseAuth);
	}

	@Test
	void rejectsInvalidEmailAndPasswordBeforeCallingSupabase() throws Exception {
		mockMvc.perform(post("/api/v1/auth/sign-up")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"not-an-email","password":"lettersOnly"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.error.message").value("비밀번호는 8~16자의 영문자와 숫자를 포함해야 합니다."));
	}

	@Test
	void logsInThroughSupabaseAndCreatesTheTrackDropProfile() throws Exception {
		String email = uniqueEmail();
		stubLogin(email);

		MvcResult result = login(email, false)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.account.email").value(email))
			.andExpect(jsonPath("$.account.publicNickname").isNotEmpty())
			.andExpect(jsonPath("$.account.emailVerified").value(true))
			.andExpect(jsonPath("$.quota.limit").value(4))
			.andReturn();

		Cookie sessionCookie = result.getResponse().getCookie("TRACKDROP_SESSION");
		mockMvc.perform(get("/api/v1/me").cookie(sessionCookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.account.email").value(email));
	}

	@Test
	void keepsRememberedLoginForSevenDaysAndLogsOutCurrentSession() throws Exception {
		String email = uniqueEmail();
		stubLogin(email);

		MvcResult login = login(email, true)
			.andExpect(status().isOk())
			.andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(
				org.hamcrest.Matchers.allOf(
					containsString("TRACKDROP_SESSION="),
					containsString("Max-Age=604800"),
					containsString("HttpOnly"),
					containsString("SameSite=Lax")))))
			.andReturn();

		Cookie sessionCookie = login.getResponse().getCookie("TRACKDROP_SESSION");
		mockMvc.perform(post("/api/v1/auth/logout").cookie(sessionCookie).with(csrf()))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/me").cookie(sessionCookie))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void returnsGenericMessageForInvalidCredentials() throws Exception {
		String email = uniqueEmail();
		when(supabaseAuth.signIn(email, "wrongpass1"))
			.thenThrow(IdentityException.invalidCredentials());

		mockMvc.perform(post("/api/v1/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"wrongpass1","rememberMe":false}
					""".formatted(email)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
			.andExpect(jsonPath("$.error.message").value("로그인 정보를 확인해 주세요."));
	}

	@Test
	void requestsAPasswordRecoveryEmailThroughSupabase() throws Exception {
		String email = uniqueEmail();
		mockMvc.perform(post("/api/v1/auth/password-recovery")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s"}
					""".formatted(email)))
			.andExpect(status().isAccepted());

		verify(supabaseAuth).requestPasswordRecovery(email);
	}

	@Test
	void updatesThePasswordWithASupabaseRecoveryToken() throws Exception {
		mockMvc.perform(post("/api/v1/auth/password-reset")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"accessToken":"recovery-access-token","password":"changed5555"}
					"""))
			.andExpect(status().isNoContent());

		verify(supabaseAuth).updatePassword("recovery-access-token", "changed5555");
	}

	private void stubLogin(String email) {
		when(supabaseAuth.signIn(email, "chatgpt5555"))
			.thenReturn(new SupabaseAuthGateway.AuthenticatedUser(
				UUID.randomUUID(), email, Instant.now()));
	}

	private org.springframework.test.web.servlet.ResultActions login(String email, boolean rememberMe)
		throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"email":"%s","password":"chatgpt5555","rememberMe":%s}
				""".formatted(email, rememberMe)));
	}

	private static String uniqueEmail() {
		return UUID.randomUUID().toString().substring(0, 8) + "@example.com";
	}
}
