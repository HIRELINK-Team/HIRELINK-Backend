package com.hirelink.global.exception;

public class RecommendationNotFoundException extends RuntimeException {

	public RecommendationNotFoundException() {
		super("추천을 찾을 수 없습니다.");
	}
}
