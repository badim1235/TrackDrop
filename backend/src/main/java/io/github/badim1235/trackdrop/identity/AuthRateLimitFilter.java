package io.github.badim1235.trackdrop.identity;

import io.github.badim1235.trackdrop.identity.IdentityException.RateLimitedIdentityException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

	private final AuthRateLimiter rateLimiter;
	private final ClientIpHasher ipHasher;

	public AuthRateLimitFilter(AuthRateLimiter rateLimiter, ClientIpHasher ipHasher) {
		this.rateLimiter = rateLimiter;
		this.ipHasher = ipHasher;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String method = request.getMethod();
		String uri = request.getRequestURI();
		boolean loginOrSignup = "POST".equals(method)
			&& (uri.equals("/api/v1/auth/login")
				|| uri.equals("/api/v1/auth/sign-up")
				|| uri.equals("/api/v1/auth/password-recovery")
				|| uri.equals("/api/v1/auth/password-reset"));
		return !loginOrSignup;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		try {
			rateLimiter.checkAuthRequest(ipHasher.hash(request.getRemoteAddr()));
			filterChain.doFilter(request, response);
		}
		catch (RateLimitedIdentityException exception) {
			response.setStatus(exception.getStatus());
			response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()));
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding("UTF-8");
			response.getWriter().printf(
				"{\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"%s\"}}",
				exception.getMessage());
		}
	}
}
