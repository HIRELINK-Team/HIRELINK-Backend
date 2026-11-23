package com.hirelink.domain.student.service;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisaExpirationSmsTimingTest {

	@Test
	void followsMinusMonthsAcrossMonthEndAndLeapYear() {
		assertThat(VisaExpirationSmsTiming.dueOn(
				LocalDate.of(2027, 3, 31), LocalDate.of(2027, 2, 28)))
				.isEqualTo(VisaExpirationSmsTiming.MONTH);
		assertThat(VisaExpirationSmsTiming.dueOn(
				LocalDate.of(2024, 3, 31), LocalDate.of(2024, 2, 29)))
				.isEqualTo(VisaExpirationSmsTiming.MONTH);
		assertThat(VisaExpirationSmsTiming.dueOn(
				LocalDate.of(2024, 2, 29), LocalDate.of(2024, 1, 29)))
				.isEqualTo(VisaExpirationSmsTiming.MONTH);
	}

	@Test
	void returnsWeekDayAndNoNotificationOnExactDates() {
		LocalDate expiryDate = LocalDate.of(2027, 6, 30);

		assertThat(VisaExpirationSmsTiming.dueOn(expiryDate, expiryDate.minusWeeks(1)))
				.isEqualTo(VisaExpirationSmsTiming.WEEK);
		assertThat(VisaExpirationSmsTiming.dueOn(expiryDate, expiryDate))
				.isEqualTo(VisaExpirationSmsTiming.DAY);
		assertThat(VisaExpirationSmsTiming.dueOn(expiryDate, expiryDate.minusDays(2))).isNull();
	}
}
