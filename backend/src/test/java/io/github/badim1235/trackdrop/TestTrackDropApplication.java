package io.github.badim1235.trackdrop;

import org.springframework.boot.SpringApplication;

public class TestTrackDropApplication {

	public static void main(String[] args) {
		SpringApplication.from(TrackDropApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
