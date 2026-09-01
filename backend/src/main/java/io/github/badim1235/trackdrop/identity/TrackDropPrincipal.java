package io.github.badim1235.trackdrop.identity;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record TrackDropPrincipal(
	UUID userId,
	String username,
	AccountStatus status
) implements UserDetails, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public String getPassword() {
		return "";
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_USER"));
	}

	@Override
	public boolean isAccountNonLocked() {
		return status == AccountStatus.ACTIVE;
	}

	@Override
	public boolean isEnabled() {
		return status != AccountStatus.WITHDRAWN;
	}
}
