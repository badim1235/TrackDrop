package io.github.badim1235.trackdrop.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthSessionService {

	private final SecurityContextRepository securityContextRepository;
	private final RememberedSessionCookie rememberedCookie;
	private final int sessionSeconds;

	public AuthSessionService(
		SecurityContextRepository securityContextRepository,
		RememberedSessionCookie rememberedCookie,
		@Value("${trackdrop.security.remember-me-seconds}") long sessionSeconds
	) {
		this.securityContextRepository = securityContextRepository;
		this.rememberedCookie = rememberedCookie;
		this.sessionSeconds = Math.toIntExact(sessionSeconds);
	}

	public TrackDropPrincipal establish(
		UserAccount account,
		boolean rememberMe,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		if (account.getStatus() != AccountStatus.ACTIVE) {
			throw IdentityException.accountSuspended();
		}
		TrackDropPrincipal principal = new TrackDropPrincipal(
			account.getId(), account.getEmail(), account.getStatus());
		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
			principal, null, principal.getAuthorities());
		save(authentication, rememberMe, request, response);
		return principal;
	}

	private void save(
		Authentication authentication,
		boolean rememberMe,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		HttpSession session = request.getSession(true);
		request.changeSessionId();
		session = request.getSession(false);
		session.setMaxInactiveInterval(sessionSeconds);
		if (rememberMe) {
			session.setAttribute(RememberedSessionCookie.REMEMBERED_ATTRIBUTE, true);
			rememberedCookie.refresh(response, session);
		}
		else {
			session.removeAttribute(RememberedSessionCookie.REMEMBERED_ATTRIBUTE);
		}

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
	}
}
