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
class StudentRegistrationRenewalMigrationIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17-alpine");
    @Test void upgradeAddsOnlyTemporaryRenewalStateAndPreservesConfirmedRegistration() {
        var source=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        Flyway.configure().dataSource(source).target("32").load().migrate();
        var jdbc=new JdbcTemplate(source);
        jdbc.update("insert into user_account(id,email,role,name) values (1,'migration@example.com','STUDENT','학생')");
        jdbc.update("insert into student_registration_verification(user_id,nationality,visa_code,stay_expires_on,card_verified_at,document_verified_at,registered_at,birth_date) values (1,'VN','D-2','2027-01-01',now(),now(),now(),'2000-01-01')");
        var before=jdbc.queryForMap("select * from student_registration_verification where user_id=1");
        var flyway=Flyway.configure().dataSource(source).target("33").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1); flyway.validate();
        var after=jdbc.queryForMap("select * from student_registration_verification where user_id=1");
        assertThat(after).containsAllEntriesOf(before).hasSize(before.size()+7);
        after.entrySet().stream().filter(e->e.getKey().startsWith("renewal_")).forEach(e->assertThat(e.getValue()).isNull());
        assertThatThrownBy(()->jdbc.update("update student_registration_verification set renewal_id=? where user_id=1",java.util.UUID.randomUUID()))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}
