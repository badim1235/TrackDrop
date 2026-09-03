package io.github.badim1235.trackdrop.catalog;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppleItunesProperties.class)
class MusicCatalogConfiguration {

	@Bean
	RestClient appleItunesRestClient(AppleItunesProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(properties.connectTimeout())
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());

		return RestClient.builder()
			.baseUrl(properties.baseUrl().toString())
			.requestFactory(requestFactory)
			.build();
	}

	@Bean
	Clock musicCatalogClock() {
		return Clock.systemUTC();
	}

	@Bean
	ProviderCallRateLimiter providerCallRateLimiter(AppleItunesProperties properties, Clock musicCatalogClock) {
		return new ProviderCallRateLimiter(properties.callsPerMinute(), musicCatalogClock);
	}

	@Bean
	Cache<String, java.util.List<MusicCatalogTrack>> musicSearchCache(AppleItunesProperties properties) {
		return Caffeine.newBuilder()
			.maximumSize(properties.cacheMaximumSize())
			.expireAfterWrite(properties.cacheTtl())
			.build();
	}
}
