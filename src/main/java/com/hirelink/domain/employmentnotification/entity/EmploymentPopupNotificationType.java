package com.hirelink.domain.employmentnotification.entity;

public enum EmploymentPopupNotificationType {

	APPLICATION_CREATED("새로운 지원자가 있습니다. 서비스에서 확인해 주세요."),
	APPLICATION_REJECTED("지원하신 일자리의 채용 결과를 서비스에서 확인해 주세요."),
	OFFER_CREATED("새로운 채용 제안이 도착했습니다. 서비스에서 응답해 주세요."),
	OFFER_ACCEPTED("채용 제안이 수락되었습니다. 서비스에서 확인해 주세요."),
	OFFER_REJECTED("채용 제안이 거절되었습니다. 서비스에서 확인해 주세요.");

	private final String message;

	EmploymentPopupNotificationType(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}
}
