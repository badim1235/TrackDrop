package io.github.badim1235.trackdrop.chart;

public final class DailyChartException extends RuntimeException {
	private final int status;
	private final String code;

	private DailyChartException(int status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	static DailyChartException futureDate() {
		return new DailyChartException(400, "FUTURE_DATE_NOT_ALLOWED", "오늘 이후의 차트는 조회할 수 없습니다.");
	}

	static DailyChartException genreNotFound() {
		return new DailyChartException(400, "GENRE_NOT_FOUND", "존재하지 않는 장르입니다.");
	}

	static DailyChartException invalidCursor(Throwable cause) {
		DailyChartException exception = new DailyChartException(400, "INVALID_CURSOR", "더 보기 정보를 다시 확인해 주세요.");
		exception.initCause(cause);
		return exception;
	}

	static DailyChartException rankingNotAvailable() {
		return new DailyChartException(404, "RANKING_NOT_AVAILABLE", "아직 확정되지 않은 과거 차트입니다.");
	}

	public int getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}
}
