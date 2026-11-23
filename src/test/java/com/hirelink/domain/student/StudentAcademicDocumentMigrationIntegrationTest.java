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
class StudentAcademicDocumentMigrationIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17-alpine");
    @Test void upgradesExistingEnrollmentWithoutLosingOriginalAndAllowsIndependentKinds() {
        var source=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        Flyway.configure().dataSource(source).target("30").load().migrate();
        var jdbc=new JdbcTemplate(source);
        jdbc.update("insert into user_account(id,email,role,name) values (1,'migration@example.com','STUDENT','학생')");
        jdbc.update("insert into student_profile(id,birth_date,nationality,current_region,korean_level) values (1,'2000-01-01','VN','서울','BEGINNER')");
        jdbc.update("insert into student_enrollment_document(user_id,object_key,university_name,grade_level,registered_at) values (1,'enrollment/1/original','학교',4,now())");
        var before=jdbc.queryForMap("select object_key,university_name,grade_level,registered_at from student_enrollment_document where user_id=1");
        var flyway=Flyway.configure().dataSource(source).target("32").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2); flyway.validate();
        assertThat(jdbc.queryForMap("select object_key,university_name,grade_level,registered_at from student_enrollment_document where user_id=1 and document_type='ENROLLMENT'")).isEqualTo(before);
        jdbc.update("insert into student_enrollment_document(user_id,document_type,object_key,topik_level,registered_at) values (1,'TOPIK','topik/1/original',4,now())");
        jdbc.update("insert into student_enrollment_document(user_id,document_type,object_key,average_gpa,academic_year,semester,registered_at) values (1,'TRANSCRIPT','transcript/1/original',4.24,2025,'SECOND',now())");
        assertThat(jdbc.queryForObject("select count(*) from student_enrollment_document where user_id=1",Integer.class)).isEqualTo(3);
        assertThatThrownBy(()->jdbc.update("update student_enrollment_document set topik_level=7 where document_type='TOPIK'")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select account_version from user_account where id=1",Long.class)).isZero();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}
