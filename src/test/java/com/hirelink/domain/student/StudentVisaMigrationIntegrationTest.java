package com.hirelink.domain.student;

import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class StudentVisaMigrationIntegrationTest {

	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	@Test
	void upgradeAddsParentCodesWithoutChangingExistingMembersOrDetailedTypes() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(
				POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
		Flyway.configure().dataSource(dataSource).target("20").load().migrate();
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.update("insert into user_account(id, email, role, name) values (1, 'migration@example.com', 'STUDENT', '기존 회원')");
		jdbc.update("""
				insert into student_profile(id, birth_date, nationality, current_region, korean_level,
				visa_code, academic_state, university_name)
				values (1, '2000-03-14', 'VN', '서울 마포구', 'INTERMEDIATE', 'D-2-2', 'ENROLLED', '기존 학교')
				""");
		Map<String, Object> userBefore = jdbc.queryForMap("select * from user_account where id = 1");
		Map<String, Object> profileBefore = jdbc.queryForMap("select * from student_profile where id = 1");
		List<Map<String, Object>> visasBefore = jdbc.queryForList("select * from visa_type order by visa_code");

		Flyway flyway = Flyway.configure().dataSource(dataSource).target("21").load();
		assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
		flyway.validate();
		assertThat(jdbc.queryForMap("select * from user_account where id = 1")).isEqualTo(userBefore);
		assertThat(jdbc.queryForMap("select * from student_profile where id = 1")).isEqualTo(profileBefore);
		assertThat(jdbc.queryForList("select * from visa_type where visa_code not in ('D-2', 'D-4') order by visa_code"))
				.isEqualTo(visasBefore);
		assertThat(jdbc.queryForList("select visa_code from visa_type where visa_code in ('D-2', 'D-4') order by visa_code",
				String.class)).containsExactly("D-2", "D-4");
		jdbc.update("update student_profile set visa_code = 'D-2' where id = 1");
		assertThat(jdbc.queryForObject("select visa_code from student_profile where id = 1", String.class)).isEqualTo("D-2");
		assertThat(flyway.migrate().migrationsExecuted).isZero();
	}
}
