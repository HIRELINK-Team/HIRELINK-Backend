package com.hirelink.global.exception;

public class MatchingReferenceNotFoundException extends RuntimeException {

	public MatchingReferenceNotFoundException() {
		super("존재하지 않는 업종 또는 우선순위 기준입니다.");
	}
}
