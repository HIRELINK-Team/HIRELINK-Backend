package com.hirelink.domain.student;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class StudentStudyProgramMigrationIntegrationTest {

	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	@Test
	void addsFiveProgramsWithoutReplacingExistingIdsOrChangingLegacyProfiles() {
		var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
		Flyway.configure().dataSource(dataSource).target("28").load().migrate();
		var jdbc = new JdbcTemplate(dataSource);
		jdbc.update("insert into study_program(id,program_name) values (73,'학사'),(88,'기존 별도 과정')");
		jdbc.update("insert into user_account(id,email,role,name) values (1,'migration@example.com','STUDENT','기존 회원')");
		jdbc.update("""
				insert into student_profile(id,birth_date,nationality,current_region,korean_level,
				visa_code,study_program_id,university_name,grade_level,academic_state)
				values (1,'2000-01-01','VN','서울','BEGINNER','D-4-1',88,'기존 학교',2,'ON_LEAVE')
				""");
		var before = jdbc.queryForMap("select * from student_profile where id = 1");
		var visas = jdbc.queryForList("select * from visa_type order by visa_code");
		Flyway flyway = Flyway.configure().dataSource(dataSource).target("29").load();
		assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
		flyway.validate();
		assertThat(jdbc.queryForList("select program_name from study_program", String.class))
				.containsExactlyInAnyOrder("전문학사", "학사", "석사", "박사", "교환학생", "기존 별도 과정");
		assertThat(jdbc.queryForObject("select id from study_program where program_name = '학사'", Long.class)).isEqualTo(73);
		assertThat(jdbc.queryForMap("select * from student_profile where id = 1")).isEqualTo(before);
		assertThat(jdbc.queryForList("select * from visa_type order by visa_code")).isEqualTo(visas);
		assertThat(flyway.migrate().migrationsExecuted).isZero();
	}
}
