package com.hirelink.global.exception;

public class DuplicateStudentProfileException extends RuntimeException {

	public DuplicateStudentProfileException() {
		super("이미 유학생 프로필이 존재합니다.");
	}
}
