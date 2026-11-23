package com.hirelink.domain.student;

import java.util.HashMap;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class StudentWorkingConditionsMigrationIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Test void migrationPreservesProfileAndBadgeWithoutInventingAnEstimate() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).target("39").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("insert into user_account(id,email,role,name) values (1,'migration@example.com','STUDENT','학생')");
        jdbc.update("""
                insert into student_profile(id,birth_date,nationality,current_region,korean_level,hirelink_badge_issued_at)
                values (1,'2000-01-01','VN','서울','BEGINNER','2026-11-23T00:00:00Z')
                """);
        var account = jdbc.queryForMap("select * from user_account where id=1");
        var before = jdbc.queryForMap("select * from student_profile where id=1");

        var flyway = Flyway.configure().dataSource(source).target("40").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        flyway.validate();

        var after = new HashMap<>(jdbc.queryForMap("select * from student_profile where id=1"));
        assertThat(after).containsEntry("working_conditions", null);
        after.remove("working_conditions");
        assertThat(after).isEqualTo(before);
        assertThat(jdbc.queryForMap("select * from user_account where id=1")).isEqualTo(account);
        assertThat(jdbc.queryForObject("select data_type from information_schema.columns where table_name='student_profile' and column_name='working_conditions'", String.class)).isEqualTo("jsonb");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}
