package com.hirelink.domain.student.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class VisaExpirationSmsScheduler {

	static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	private final VisaExpirationSmsNotificationService notifications;
	private final Clock clock;

	public VisaExpirationSmsScheduler(VisaExpirationSmsNotificationService notifications, Clock clock) {
		this.notifications = notifications;
		this.clock = clock;
	}

	@Scheduled(cron = "${visa.expiration.sms.cron:0 0 9 * * *}", zone = "Asia/Seoul")
	public void sendNotifications() {
		notifications.sendDueNotifications(LocalDate.now(clock.withZone(KOREA_ZONE)));
	}
}
