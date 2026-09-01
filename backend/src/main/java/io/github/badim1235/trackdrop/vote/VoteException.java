package io.github.badim1235.trackdrop.vote;

import io.github.badim1235.trackdrop.shared.quota.DailyQuotaSnapshot;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class VoteException extends RuntimeException {
	private final String code;
	private final HttpStatus status;
	private final Map<String, Object> details;

	private VoteException(
		String code,
		String message,
		HttpStatus status,
		Map<String, Object> details,
		Throwable cause
	) {
		super(message, cause);
		this.code = code;
		this.status = status;
		this.details = details;
	}

	static VoteException trackNotFound() {
		return new VoteException(
			"TRACK_NOT_FOUND",
			"등록된 곡을 찾을 수 없습니다.",
			HttpStatus.NOT_FOUND,
			Map.of(),
			null);
	}

	static VoteException alreadyVoted(
		UUID trackId,
		LocalDate votedOn,
		DailyQuotaSnapshot quota,
		Throwable cause
	) {
		return new VoteException(
			"ALREADY_VOTED",
			"오늘 이미 추천한 곡입니다.",
			HttpStatus.CONFLICT,
			Map.of(
				"trackId", trackId.toString(),
				"votedOn", votedOn.toString(),
				"quota", quota),
			cause);
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
}
