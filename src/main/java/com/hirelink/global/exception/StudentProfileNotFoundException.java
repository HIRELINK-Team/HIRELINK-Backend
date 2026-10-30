package com.hirelink.global.exception;

public class StudentProfileNotFoundException extends RuntimeException {

	public StudentProfileNotFoundException() {
		super("유학생 프로필이 아직 등록되지 않았습니다.");
	}
}
