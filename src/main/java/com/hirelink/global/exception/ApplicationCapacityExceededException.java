package com.hirelink.global.exception;

public class ApplicationCapacityExceededException extends RuntimeException {

	public ApplicationCapacityExceededException() {
		super("해당 채용공고는 현재 검토 가능한 지원자 수를 초과했습니다.");
	}
}
