package com.hirelink.domain.employmentnotification;

enum EmploymentSmsEventType {

	APPLICATION_CREATED("employment-application-created",
			"[HIRELINK] 새로운 지원자가 있습니다. 서비스에서 확인해 주세요."),
	APPLICATION_REJECTED("employment-application-rejected",
			"[HIRELINK] 지원하신 일자리의 채용 결과를 서비스에서 확인해 주세요."),
	OFFER_CREATED("employment-offer-created",
			"[HIRELINK] 새로운 채용 제안이 도착했습니다. 서비스에서 응답해 주세요."),
	OFFER_ACCEPTED("employment-offer-accepted",
			"[HIRELINK] 채용 제안이 수락되었습니다. 서비스에서 확인해 주세요."),
	OFFER_REJECTED("employment-offer-rejected",
			"[HIRELINK] 채용 제안이 거절되었습니다. 서비스에서 확인해 주세요.");

	private final String idempotencyKeyPrefix;
	private final String message;

	EmploymentSmsEventType(String idempotencyKeyPrefix, String message) {
		this.idempotencyKeyPrefix = idempotencyKeyPrefix;
		this.message = message;
	}

	String idempotencyKey(Long entityId) {
		return idempotencyKeyPrefix + ":" + entityId;
	}

	String message() {
		return message;
	}
}
