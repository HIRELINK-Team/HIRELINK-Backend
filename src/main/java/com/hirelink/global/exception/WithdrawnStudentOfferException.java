package com.hirelink.global.exception;

public class WithdrawnStudentOfferException extends RuntimeException {

	public WithdrawnStudentOfferException() {
		super("탈퇴를 신청한 유학생에게는 채용 제안을 생성할 수 없습니다.");
	}
}
