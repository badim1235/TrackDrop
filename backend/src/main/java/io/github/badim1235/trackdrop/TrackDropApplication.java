package io.github.badim1235.trackdrop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TrackDropApplication {

	public static void main(String[] args) {
		SpringApplication.run(TrackDropApplication.class, args);
	}

}
