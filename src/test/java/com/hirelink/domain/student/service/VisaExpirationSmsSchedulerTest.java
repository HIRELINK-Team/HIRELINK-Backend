package com.hirelink.domain.student.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VisaExpirationSmsSchedulerTest {

	@Test
	void usesTheKoreanCalendarDateAtTheUtcBoundary() {
		VisaExpirationSmsNotificationService notifications = mock(VisaExpirationSmsNotificationService.class);
		Clock clock = Clock.fixed(Instant.parse("2027-01-01T15:30:00Z"), ZoneOffset.UTC);
		VisaExpirationSmsScheduler scheduler = new VisaExpirationSmsScheduler(notifications, clock);

		scheduler.sendNotifications();

		verify(notifications).sendDueNotifications(LocalDate.of(2027, 1, 2));
	}
}
