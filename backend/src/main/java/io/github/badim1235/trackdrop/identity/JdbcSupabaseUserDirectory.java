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
		boolean authSchemaAvailable = Boolean.TRUE.equals(jdbcClient.sql("""
			SELECT TO_REGCLASS('auth.users') IS NOT NULL
			""")
			.query(Boolean.class)
			.single());
		String directoryTable = authSchemaAvailable ? "auth.users" : "users";
		String emailColumn = authSchemaAvailable ? "email" : "email_normalized";
		return Boolean.TRUE.equals(jdbcClient.sql("""
			SELECT EXISTS (
				SELECT 1
				FROM %s
				WHERE %s IS NOT NULL
					AND LOWER(%s) = :email
			)
			""".formatted(directoryTable, emailColumn, emailColumn))
			.param("email", normalizedEmail)
			.query(Boolean.class)
			.single());
	}
}
