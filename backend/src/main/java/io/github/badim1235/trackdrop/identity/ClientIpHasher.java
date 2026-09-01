package io.github.badim1235.trackdrop.identity;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClientIpHasher {

	private final byte[] secret;

	public ClientIpHasher(@Value("${trackdrop.security.ip-hash-secret}") String secret) {
		this.secret = secret.getBytes(StandardCharsets.UTF_8);
	}

	public String hash(String address) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(address.getBytes(StandardCharsets.UTF_8)));
		}
		catch (java.security.GeneralSecurityException exception) {
			throw new IllegalStateException("HmacSHA256 is not available", exception);
		}
	}
}
