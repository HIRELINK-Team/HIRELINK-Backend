package com.hirelink.domain.student;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class StudentEnrollmentMigrationIntegrationTest {
	@Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17-alpine");
	@Test void preservesLegacyProfilesAndConstrainsNewConfirmedDocumentGrades() {
		var source=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
		Flyway.configure().dataSource(source).target("29").load().migrate();
		var jdbc=new JdbcTemplate(source);
		jdbc.update("insert into user_account(id,email,role,name) values (1,'migration@example.com','STUDENT','학생')");
		jdbc.update("insert into student_profile(id,birth_date,nationality,current_region,korean_level,university_name,grade_level) values (1,'2000-01-01','VN','서울','BEGINNER','기존학교',5)");
		var before=jdbc.queryForMap("select * from student_profile where id=1");
		var flyway=Flyway.configure().dataSource(source).target("30").load();
		assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1); flyway.validate();
		assertThat(jdbc.queryForMap("select * from student_profile where id=1")).isEqualTo(before);
		assertThatThrownBy(()->jdbc.update("insert into student_enrollment_document(user_id,object_key,university_name,grade_level,registered_at) values (1,'test','학교',5,now())"))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		jdbc.update("insert into student_enrollment_document(user_id,object_key,university_name,grade_level,registered_at) values (1,'test','학교',4,now())");
		assertThat(flyway.migrate().migrationsExecuted).isZero();
	}
}
