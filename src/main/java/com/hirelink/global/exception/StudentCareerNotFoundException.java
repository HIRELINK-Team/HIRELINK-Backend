package com.hirelink.global.exception;

public class StudentCareerNotFoundException extends RuntimeException {

	public StudentCareerNotFoundException() {
		super("경력 정보를 찾을 수 없습니다.");
	}
}
