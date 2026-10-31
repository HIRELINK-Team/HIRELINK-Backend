package com.hirelink.global.exception;

public class RecommendationStateConflictException extends RuntimeException {

	public RecommendationStateConflictException() {
		super("유효한 추천으로만 지원할 수 있습니다.");
	}
}
