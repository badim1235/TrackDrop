package io.github.badim1235.trackdrop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TrackDropApplicationTests {

	@Test
	void contextLoads() {
	}
}
