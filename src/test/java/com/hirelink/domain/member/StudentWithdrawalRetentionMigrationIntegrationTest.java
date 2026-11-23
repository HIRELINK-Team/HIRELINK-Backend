package com.hirelink.domain.member;

import java.sql.Timestamp;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class StudentWithdrawalRetentionMigrationIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Test void preservesExistingDataBackfillsDeadlineAndKeepsActiveAccountConstraints() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).target("41").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("insert into user_account(id,role,name,email,phone_number) values (1,'STUDENT','유학생','student@example.com','01000000001')");
        jdbc.update("insert into student_profile(id,birth_date,nationality,current_region,korean_level) values (1,'2000-01-01','VN','서울','BEGINNER')");
        jdbc.update("insert into student_enrollment_document(user_id,document_type,object_key,topik_level,registered_at) values (1,'TOPIK','topik/1/00000000-0000-0000-0000-000000000001',4,now())");
        jdbc.update("insert into user_account(id,role,withdrawn_at,withdrawal_requested_at,withdrawal_finalized_at) values (2,'STUDENT','2028-01-30 06:00Z','2028-01-30 06:00Z','2028-02-29 06:00Z')");
        var account = jdbc.queryForList("select id,name,email,phone_number,created_at from user_account order by id");
        var document = jdbc.queryForList("select user_id,document_type,object_key,topik_level,registered_at from student_enrollment_document");
        var flyway = Flyway.configure().dataSource(source).target("42").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        flyway.validate();
        assertThat(jdbc.queryForList("select id,name,email,phone_number,created_at from user_account order by id")).isEqualTo(account);
        assertThat(jdbc.queryForList("select user_id,document_type,object_key,topik_level,registered_at from student_enrollment_document")).isEqualTo(document);
        assertThat(jdbc.queryForObject("select withdrawal_retain_until from user_account where id=2",Timestamp.class).toInstant()).isEqualTo(Instant.parse("2031-02-28T06:00:00Z"));
        assertThat(jdbc.queryForObject("select withdrawal_retain_until from user_account where id=1",Timestamp.class)).isNull();
        assertThatThrownBy(()->jdbc.update("update student_profile set birth_date=null where id=1")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("update user_account set created_at=null where id=1")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("update student_enrollment_document set object_key=null where user_id=1")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("update student_enrollment_document set deleted_at=now() where user_id=1")).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("update student_enrollment_document set deleted_at=now(),object_key=null where user_id=1");
        assertThat(jdbc.queryForObject("select topik_level from student_enrollment_document where user_id=1",Integer.class)).isEqualTo(4);
        jdbc.update("update user_account set phone_number='01000000001' where id=2");
        assertThat(jdbc.queryForObject("select count(*) from user_account where phone_number='01000000001'", Integer.class)).isEqualTo(2);
        assertThatThrownBy(()->jdbc.update("insert into user_account(id,role,phone_number) values (3,'STUDENT','01000000001')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}
