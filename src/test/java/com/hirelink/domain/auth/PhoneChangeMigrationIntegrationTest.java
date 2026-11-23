package com.hirelink.domain.auth;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class PhoneChangeMigrationIntegrationTest {
	@Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	@Test
	void upgradePreservesExistingChallengesAndRequiresPairedConfirmationTimes() {
		var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
		Flyway.configure().dataSource(source).target("34").load().migrate();
		var jdbc = new JdbcTemplate(source);
		jdbc.update("insert into user_account(id,email,role,name,phone_number) values (1,'migration@example.com','STUDENT','학생','01011112222')");
		jdbc.update("insert into phone_verification(user_id,phone_number,code_hash,expires_at) values (1,'01033334444','hash',now()+interval '5 minutes')");
		var account = jdbc.queryForMap("select * from user_account where id=1");
		var challenge = jdbc.queryForMap("select * from phone_verification where user_id=1");
		var flyway = Flyway.configure().dataSource(source).target("35").load();
		assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
		flyway.validate();
		assertThat(jdbc.queryForMap("select * from user_account where id=1")).isEqualTo(account);
		assertThat(jdbc.queryForMap("select * from phone_verification where user_id=1"))
				.containsAllEntriesOf(challenge).containsEntry("verified_at",null).containsEntry("change_expires_at",null);
		assertThatThrownBy(() -> jdbc.update("update phone_verification set verified_at=now() where user_id=1"))
				.isInstanceOf(DataIntegrityViolationException.class);
		jdbc.update("update phone_verification set verified_at=now(),change_expires_at=now()+interval '5 minutes' where user_id=1");
		assertThat(flyway.migrate().migrationsExecuted).isZero();
	}
}
