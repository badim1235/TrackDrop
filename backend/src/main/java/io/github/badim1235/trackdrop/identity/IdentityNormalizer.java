package io.github.badim1235.trackdrop.identity;

import java.text.Normalizer;

final class IdentityNormalizer {

	private IdentityNormalizer() {
	}

	static String email(String value) {
		return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).toLowerCase(java.util.Locale.ROOT);
	}
}
