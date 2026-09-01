package io.github.badim1235.trackdrop.shared.quota;

public class DailyQuotaExceededException extends RuntimeException {
	private final DailyQuotaSnapshot quota;

	DailyQuotaExceededException(DailyQuotaSnapshot quota) {
		super("오늘의 추천권을 모두 사용했습니다.");
		this.quota = quota;
	}

	public DailyQuotaSnapshot getQuota() {
		return quota;
	}
}
