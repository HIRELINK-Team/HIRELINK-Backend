package com.hirelink.global.exception;

public class ProfileImageAccessException extends RuntimeException {

	public ProfileImageAccessException() {
		super("프로필 이미지 주소를 생성하지 못했습니다.");
	}
}
