package io.github.badim1235.trackdrop.identity;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSupabaseUserDirectory implements SupabaseUserDirectory {

	private final JdbcClient jdbcClient;

	public JdbcSupabaseUserDirectory(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public boolean existsByEmail(String normalizedEmail) {
		return Boolean.TRUE.equals(jdbcClient.sql("""
			SELECT EXISTS (
				SELECT 1
				FROM auth.users
				WHERE email IS NOT NULL
					AND LOWER(email) = :email
			)
			""")
			.param("email", normalizedEmail)
			.query(Boolean.class)
			.single());
	}
}
