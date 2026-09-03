package io.github.badim1235.trackdrop.moderation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportRequest(
	@NotNull(message = "신고 사유를 선택해 주세요.") ReasonCode reasonCode,
	@Size(max = 500, message = "추가 설명은 500자 이내로 입력해 주세요.") String details
) {
	public enum ReasonCode {
		ABUSIVE_LANGUAGE,
		SPAM,
		OTHER
	}
}
