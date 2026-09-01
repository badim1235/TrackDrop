package io.github.badim1235.trackdrop.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class NicknameGeneratorTests {

	private static final Set<String> EXCLUDED_STEMS = Set.of(
		"노을빛빛",
		"은빛빛",
		"가을빛빛",
		"여름밤밤",
		"겨울밤밤",
		"바람결바람");

	@Test
	void generatesFourDigitNicknamesWithoutExcludedPairs() {
		NicknameGenerator generator = new NicknameGenerator();
		generator.loadWords();

		for (int attempt = 0; attempt < 10_000; attempt++) {
			String nickname = generator.generate();
			assertThat(nickname).matches(".+\\d{4}");
			String stem = nickname.substring(0, nickname.length() - 4);
			assertThat(stem).isNotIn(EXCLUDED_STEMS);
		}
	}
}
