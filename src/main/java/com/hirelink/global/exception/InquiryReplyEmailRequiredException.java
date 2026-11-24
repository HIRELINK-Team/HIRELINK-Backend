package com.hirelink.global.exception;

public class InquiryReplyEmailRequiredException extends RuntimeException {

	public InquiryReplyEmailRequiredException() {
		super("답변을 받을 이메일이 계정에 등록되어 있지 않습니다.");
	}
}
