package io.github.badim1235.trackdrop.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.badim1235.trackdrop.TestcontainersConfiguration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JdbcSupabaseUserDirectoryTests {
	@Autowired
	private JdbcSupabaseUserDirectory directory;

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	@Transactional
	void fallsBackToTheLocalProfileDirectoryWhenSupabaseAuthSchemaIsAbsent() {
		String email = UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcClient.sql("""
				INSERT INTO users (
					id, email, email_normalized, email_verified_at,
					public_nickname, status, created_at, updated_at
				)
				VALUES (:id, :email, :email, :now, :nickname, 'ACTIVE', :now, :now)
				""")
			.param("id", UUID.randomUUID())
			.param("email", email)
			.param("nickname", "중복확인" + UUID.randomUUID().toString().substring(0, 8))
			.param("now", now)
			.update();

		assertThat(directory.existsByEmail(email)).isTrue();
		assertThat(directory.existsByEmail("missing-" + email)).isFalse();
	}
}
