package com.hirelink.domain.employmentnotification;

record EmploymentSmsRequested(EmploymentSmsEventType type, Long entityId, String recipient) {

	@Override
	public String toString() {
		return "EmploymentSmsRequested[type=" + type + ", entityId=" + entityId + ", recipient=***]";
	}
}
