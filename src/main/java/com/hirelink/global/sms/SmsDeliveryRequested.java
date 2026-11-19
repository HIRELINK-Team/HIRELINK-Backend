package com.hirelink.global.sms;

record SmsDeliveryRequested(Long historyId, String recipient, String message) {

	@Override
	public String toString() {
		return "SmsDeliveryRequested[historyId=" + historyId + ", recipient=***, message=***]";
	}
}
