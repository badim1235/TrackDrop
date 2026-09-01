package io.github.badim1235.trackdrop.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class RememberedSessionFilter extends OncePerRequestFilter {

	private final RememberedSessionCookie cookie;

	public RememberedSessionFilter(RememberedSessionCookie cookie) {
		this.cookie = cookie;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		if (session != null && Boolean.TRUE.equals(session.getAttribute(RememberedSessionCookie.REMEMBERED_ATTRIBUTE))) {
			cookie.refresh(response, session);
		}
		filterChain.doFilter(request, response);
	}
}
