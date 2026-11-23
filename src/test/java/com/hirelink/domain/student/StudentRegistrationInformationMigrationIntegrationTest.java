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
class StudentRegistrationInformationMigrationIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17-alpine");

    @Test void preservesExistingDataAndAllowsVerifiedVisaOutsideReferenceCatalog() {
        var source=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        Flyway.configure().dataSource(source).target("35").load().migrate();
        var jdbc=new JdbcTemplate(source);
        jdbc.update("insert into user_account(id,email,role,name) values (1,'migration@example.com','STUDENT','학생')");
        jdbc.update("insert into student_profile(id,birth_date,nationality,current_region,korean_level,visa_code) values (1,'2000-01-01','VN','서울','BEGINNER','D-2-2')");
        jdbc.update("insert into student_registration_verification(user_id,nationality,visa_code,stay_expires_on,card_verified_at,registered_at) values (1,'VN','D-2','2027-01-01',now(),now())");
        var profile=jdbc.queryForMap("select * from student_profile where id=1");
        var verification=jdbc.queryForMap("select * from student_registration_verification where user_id=1");
        var catalog=jdbc.queryForList("select * from visa_type order by visa_code");
        var flyway=Flyway.configure().dataSource(source).target("36").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1); flyway.validate();
        assertThat(jdbc.queryForMap("select * from student_profile where id=1")).isEqualTo(profile);
        assertThat(jdbc.queryForMap("select * from student_registration_verification where user_id=1")).containsAllEntriesOf(verification);
        assertThat(jdbc.queryForObject("select expiry_notice_dates::text from student_registration_verification where user_id=1",String.class)).isEqualTo("[]");
        jdbc.update("update student_profile set visa_code='D-10' where id=1");
        assertThat(jdbc.queryForObject("select visa_code from student_profile where id=1",String.class)).isEqualTo("D-10");
        jdbc.update("update student_registration_verification set visa_code='D-10' where user_id=1");
        assertThat(jdbc.queryForObject("select visa_code from student_registration_verification where user_id=1",String.class)).isEqualTo("D-10");
        assertThat(jdbc.queryForList("select * from visa_type order by visa_code")).isEqualTo(catalog);
        assertThatThrownBy(()->jdbc.update("update student_profile set study_program_id=-1 where id=1")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("update student_registration_verification set expiry_notice_dates='{}'::jsonb where user_id=1")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}
