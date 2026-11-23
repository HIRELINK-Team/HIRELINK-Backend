package com.hirelink.domain.member;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class TermVersionMigrationIntegrationTest {
	@Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	@Test void migrationPreservesLegacyConsentWithoutInventingBodyVersions() {
		var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
		Flyway.configure().dataSource(source).target("35").load().migrate();
		var jdbc = new JdbcTemplate(source);
		jdbc.update("insert into user_account(id,email,role) values (1,'terms@example.com','STUDENT')");
		jdbc.update("insert into user_term_agreement(user_id,term_type_id,is_agreed,agreed_at) values (1,1,true,'2026-01-01T00:00:00Z'),(1,2,false,null)");
		jdbc.update("insert into social_auth_session(token_hash,provider,provider_user_id,role,created_at,expires_at) values ('legacy','GOOGLE','legacy','STUDENT',now(),now()+interval '30 minutes')");
		jdbc.update("insert into social_auth_session_term_agreement(token_hash,term_type_id,is_agreed,agreed_at) values ('legacy',4,true,'2026-01-01T00:00:00Z')");
		var users = jdbc.queryForList("select user_id,term_type_id,is_agreed,agreed_at from user_term_agreement order by term_type_id");
		var sessions = jdbc.queryForList("select token_hash,term_type_id,is_agreed,agreed_at from social_auth_session_term_agreement");
		var flyway = Flyway.configure().dataSource(source).target("37").load();
		flyway.migrate(); flyway.validate();
		assertThat(jdbc.queryForList("select user_id,term_type_id,is_agreed,agreed_at from user_term_agreement order by term_type_id")).isEqualTo(users);
		assertThat(jdbc.queryForList("select token_hash,term_type_id,is_agreed,agreed_at from social_auth_session_term_agreement")).isEqualTo(sessions);
		assertThat(jdbc.queryForObject("select count(*) from user_term_agreement where term_version is not null",Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from social_auth_session_term_agreement where term_version is not null",Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from term_type where current_version is not null",Integer.class)).isZero();
		jdbc.update("update term_type set current_version='body-2026-11' where id=1");
		assertThat(jdbc.queryForObject("select term_version from user_term_agreement where term_type_id=1",String.class)).isNull();
		assertThatThrownBy(() -> jdbc.update("update term_type set current_version=' ' where id=1"))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		assertThat(flyway.migrate().migrationsExecuted).isZero();

		var initialization = Flyway.configure().dataSource(source).target("38").load();
		assertThat(initialization.migrate().migrationsExecuted).isEqualTo(1);
		initialization.validate();
		assertThat(jdbc.queryForObject("select current_version from term_type where id=1", String.class))
				.isEqualTo("body-2026-11");
		assertThat(jdbc.queryForList("select current_version from term_type where id<>1", String.class))
				.isNotEmpty().containsOnly("v1");
		assertThat(jdbc.queryForList("select user_id,term_type_id,is_agreed,agreed_at from user_term_agreement order by term_type_id"))
				.isEqualTo(users);
		assertThat(jdbc.queryForList("select token_hash,term_type_id,is_agreed,agreed_at from social_auth_session_term_agreement"))
				.isEqualTo(sessions);
		assertThat(jdbc.queryForObject("select count(*) from user_term_agreement where term_version is not null", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from social_auth_session_term_agreement where term_version is not null", Integer.class)).isZero();
		assertThat(initialization.migrate().migrationsExecuted).isZero();
	}
}
