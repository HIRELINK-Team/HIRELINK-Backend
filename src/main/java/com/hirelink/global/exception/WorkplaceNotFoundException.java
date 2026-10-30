package com.hirelink.global.exception;

public class WorkplaceNotFoundException extends RuntimeException {

	public WorkplaceNotFoundException() {
		super("사업장 정보를 찾을 수 없습니다.");
	}
}
