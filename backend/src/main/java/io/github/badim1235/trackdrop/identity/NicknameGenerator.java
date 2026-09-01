package io.github.badim1235.trackdrop.identity;

import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class NicknameGenerator {

	private final SecureRandom random = new SecureRandom();
	private List<String> prefixes;
	private List<String> nouns;
	private Set<WordPair> excludedPairs;

	@PostConstruct
	void loadWords() {
		YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
		factory.setResources(new ClassPathResource("nickname-words.yml"));
		Properties properties = factory.getObject();
		if (properties == null) {
			throw new IllegalStateException("Nickname word resource could not be loaded");
		}

		prefixes = readList(properties, "prefixes");
		nouns = readList(properties, "nouns");
		excludedPairs = new HashSet<>();
		for (int index = 0; properties.containsKey("excludedPairs[" + index + "].prefix"); index++) {
			excludedPairs.add(new WordPair(
				properties.getProperty("excludedPairs[" + index + "].prefix"),
				properties.getProperty("excludedPairs[" + index + "].noun")));
		}

		if (prefixes.size() != 50 || nouns.size() != 50 || excludedPairs.size() != 6) {
			throw new IllegalStateException("Nickname dictionary must contain 50 prefixes, 50 nouns and 6 exclusions");
		}
	}

	public String generate() {
		WordPair pair;
		do {
			pair = new WordPair(
				prefixes.get(random.nextInt(prefixes.size())),
				nouns.get(random.nextInt(nouns.size())));
		} while (excludedPairs.contains(pair));

		return pair.prefix() + pair.noun() + "%04d".formatted(random.nextInt(10_000));
	}

	private static List<String> readList(Properties properties, String name) {
		List<String> values = new ArrayList<>();
		for (int index = 0; properties.containsKey(name + "[" + index + "]"); index++) {
			values.add(properties.getProperty(name + "[" + index + "]"));
		}
		return List.copyOf(values);
	}

	private record WordPair(String prefix, String noun) {
	}
}
