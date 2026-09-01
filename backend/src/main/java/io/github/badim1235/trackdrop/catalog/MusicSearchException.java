package io.github.badim1235.trackdrop.catalog;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class MusicSearchException extends RuntimeException {
	private final String code;
	private final HttpStatus status;
	private final Map<String, Object> details;
	private final Long retryAfterSeconds;

	private MusicSearchException(
		String code,
		String message,
		HttpStatus status,
		Map<String, Object> details,
		Long retryAfterSeconds
	) {
		super(message);
		this.code = code;
		this.status = status;
		this.details = details;
		this.retryAfterSeconds = retryAfterSeconds;
	}

	static MusicSearchException invalidQuery() {
		return new MusicSearchException(
			"SEARCH_QUERY_INVALID",
			"검색어는 2~100자로 입력해 주세요.",
			HttpStatus.BAD_REQUEST,
			Map.of(),
			null);
	}

	static MusicSearchException rateLimited(long retryAfterSeconds) {
		return new MusicSearchException(
			"RATE_LIMITED",
			"음악 검색 요청이 많습니다. 잠시 후 다시 시도해 주세요.",
			HttpStatus.TOO_MANY_REQUESTS,
			Map.of("retryable", true),
			retryAfterSeconds);
	}

	static MusicSearchException providerUnavailable() {
		return new MusicSearchException(
			"MUSIC_PROVIDER_UNAVAILABLE",
			"음악 검색 서비스에 잠시 연결할 수 없습니다.",
			HttpStatus.SERVICE_UNAVAILABLE,
			Map.of("retryable", true),
			null);
	}

	public String getCode() {
		return code;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public Map<String, Object> getDetails() {
		return details;
	}

	public Long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}
}
