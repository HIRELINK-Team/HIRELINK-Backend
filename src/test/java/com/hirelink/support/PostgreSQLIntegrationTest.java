package com.hirelink.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class PostgreSQLIntegrationTest {

	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	static {
		POSTGRES.start();
	}

	@Autowired
	private JdbcTemplate jdbc;

	@DynamicPropertySource
	protected static void registerSharedPostgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("application.expiration.initial-delay", () -> "3600000");
	}

	@BeforeEach
	void restoreSharedReferenceData() {
		jdbc.execute("DROP TRIGGER IF EXISTS fail_employment_offer_update ON employment_offer");
		jdbc.execute("DROP FUNCTION IF EXISTS fail_employment_offer_update()");
		jdbc.execute("TRUNCATE TABLE business_type, study_program RESTART IDENTITY CASCADE");
		jdbc.update("""
				INSERT INTO business_type (id, name) VALUES
				(1, 'FOOD_CAFE'),
				(2, 'RETAIL'),
				(3, 'OFFICE_EVENT_TOURISM'),
				(4, 'INTERPRETATION_TRANSLATION'),
				(5, 'DUTY_FREE'),
				(6, 'MAJOR_RELATED'),
				(7, 'INTERNSHIP'),
				(8, 'OTHER')
				""");
		jdbc.execute("SELECT setval(pg_get_serial_sequence('business_type', 'id'), 8)");
		jdbc.update("DELETE FROM visa_type WHERE visa_code = 'TEST-VISA'");
	}
}
