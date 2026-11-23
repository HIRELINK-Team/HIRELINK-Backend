package com.hirelink.domain.auth;

import java.util.UUID;
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
class LoginSessionMigrationIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void addsDistinctSessionIdsWithoutDeletingExistingAccountsOrTokens() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).target("38").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("insert into user_account(id,email,role) values (1,'migration@example.com','STUDENT')");
        jdbc.update("insert into auth_account(user_id,provider,provider_user_id) values (1,'GOOGLE','original-identity')");
        jdbc.update("insert into refresh_token(user_id,token_hash,expires_at) values (1,repeat('a',64),now()+interval '1 day'),(1,repeat('b',64),now()+interval '2 days')");
        var account = jdbc.queryForList("select * from user_account");
        var social = jdbc.queryForList("select * from auth_account");
        var oldTokens = jdbc.queryForList("select * from refresh_token order by id");
        var flyway = Flyway.configure().dataSource(source).target("39").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        flyway.validate();
        assertThat(jdbc.queryForList("select * from user_account")).isEqualTo(account);
        assertThat(jdbc.queryForList("select * from auth_account")).isEqualTo(social);
        assertThat(jdbc.queryForList("select id,user_id,token_hash,expires_at,created_at from refresh_token order by id")).isEqualTo(oldTokens);
        var sessions = jdbc.queryForList("select session_id from refresh_token", UUID.class);
        assertThat(sessions).hasSize(2).doesNotContainNull().doesNotHaveDuplicates();
        assertThatThrownBy(() -> jdbc.update("update refresh_token set session_id=null where id=1")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update refresh_token set session_id=? where id=2", sessions.get(0))).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}
