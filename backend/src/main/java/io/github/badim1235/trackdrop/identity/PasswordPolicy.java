package io.github.badim1235.trackdrop.identity;

final class PasswordPolicy {

	private PasswordPolicy() {
	}

	static boolean isValid(String password) {
		if (password == null) {
			return false;
		}

		int length = password.codePointCount(0, password.length());
		if (length < 8 || length > 16) {
			return false;
		}

		boolean hasEnglishLetter = false;
		boolean hasDigit = false;
		for (int offset = 0; offset < password.length();) {
			int codePoint = password.codePointAt(offset);
			if (Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)) {
				return false;
			}
			hasEnglishLetter |= codePoint >= 'A' && codePoint <= 'Z'
				|| codePoint >= 'a' && codePoint <= 'z';
			hasDigit |= codePoint >= '0' && codePoint <= '9';
			offset += Character.charCount(codePoint);
		}
		return hasEnglishLetter && hasDigit;
	}
}
