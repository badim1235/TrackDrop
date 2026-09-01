package io.github.badim1235.trackdrop.identity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {

	private static final int NICKNAME_ATTEMPTS = 20;

	private final JdbcClient jdbcClient;
	private final UserAccountRepository userAccounts;
	private final NicknameGenerator nicknameGenerator;

	public IdentityService(
		JdbcClient jdbcClient,
		UserAccountRepository userAccounts,
		NicknameGenerator nicknameGenerator
	) {
		this.jdbcClient = jdbcClient;
		this.userAccounts = userAccounts;
		this.nicknameGenerator = nicknameGenerator;
	}

	@Transactional
	public UserAccount provision(SupabaseAuthGateway.AuthenticatedUser authenticatedUser) {
		UUID id = authenticatedUser.id();
		String normalizedEmail = IdentityNormalizer.email(authenticatedUser.email());
		OffsetDateTime emailVerifiedAt = OffsetDateTime.ofInstant(
			authenticatedUser.emailVerifiedAt(), ZoneOffset.UTC);
		var existingAccount = userAccounts.findById(id);
		if (existingAccount.isPresent()) {
			return existingAccount.get();
		}
		if (userAccounts.existsByEmailNormalized(normalizedEmail)) {
			throw IdentityException.emailTaken();
		}

		for (int attempt = 0; attempt < NICKNAME_ATTEMPTS; attempt++) {
			String nickname = nicknameGenerator.generate();
			int inserted = jdbcClient.sql("""
					INSERT INTO users (
						id, email, email_normalized, email_verified_at,
						public_nickname, status, created_at, updated_at
					)
					VALUES (
						:id, :email, :emailNormalized, :emailVerifiedAt,
						:nickname, 'ACTIVE', :now, :now
					)
					ON CONFLICT DO NOTHING
					""")
				.param("id", id)
				.param("email", authenticatedUser.email())
				.param("emailNormalized", normalizedEmail)
				.param("emailVerifiedAt", emailVerifiedAt)
				.param("nickname", nickname)
				.param("now", OffsetDateTime.now(ZoneOffset.UTC))
				.update();

			if (inserted == 1) {
				return userAccounts.findById(id).orElseThrow();
			}
			if (userAccounts.existsById(id)) {
				return userAccounts.findById(id).orElseThrow();
			}
			if (userAccounts.existsByEmailNormalized(normalizedEmail)) {
				throw IdentityException.emailTaken();
			}
		}

		throw new IllegalStateException("Could not allocate a unique public nickname");
	}

	public UserAccount account(UUID userId) {
		return userAccounts.findById(userId).orElseThrow();
	}
}
