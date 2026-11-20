package com.hirelink.global.exception;

public class EmploymentPopupNotificationNotFoundException extends RuntimeException {

	public EmploymentPopupNotificationNotFoundException() {
		super("존재하지 않는 채용 팝업 알림입니다.");
	}
}
