package com.hirelink.global.exception;

public class UnreadableBusinessRegistrationException extends RuntimeException {

	public UnreadableBusinessRegistrationException() {
		super("사업자등록증에서 사업자등록번호를 확인하지 못했습니다. 선명한 파일로 다시 시도해주세요.");
	}
}
