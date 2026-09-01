package io.github.badim1235.trackdrop.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTests {

	@Test
	void requiresEnglishLettersAndNumbersWithoutWhitespace() {
		assertThat(PasswordPolicy.isValid("chatgpt5555")).isTrue();
		assertThat(PasswordPolicy.isValid("ChatGPT5555!")).isTrue();
		assertThat(PasswordPolicy.isValid("rlawldnjs")).isFalse();
		assertThat(PasswordPolicy.isValid("55555555")).isFalse();
		assertThat(PasswordPolicy.isValid("chat gpt5555")).isFalse();
		assertThat(PasswordPolicy.isValid("short1")).isFalse();
		assertThat(PasswordPolicy.isValid("seventeenletters17")).isFalse();
	}
}
