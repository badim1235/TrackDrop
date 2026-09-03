package io.github.badim1235.trackdrop.track;

import org.springframework.http.HttpStatus;

public class TrackDetailException extends RuntimeException {
	private final String code;
	private final HttpStatus status;

	private TrackDetailException(String code, String message, HttpStatus status) {
		super(message);
		this.code = code;
		this.status = status;
	}

	static TrackDetailException notFound() {
		return new TrackDetailException(
			"TRACK_NOT_FOUND",
			"등록된 곡을 찾을 수 없습니다.",
			HttpStatus.NOT_FOUND);
	}

	public String getCode() {
		return code;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
