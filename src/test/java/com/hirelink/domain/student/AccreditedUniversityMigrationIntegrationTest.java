package com.hirelink.domain.student;

import java.nio.file.Files;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import com.hirelink.domain.student.service.AccreditedUniversityService;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class AccreditedUniversityMigrationIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @Test void migrationPreservesAccountsAndSeedsExactlyTheProvided184Entries() throws Exception {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        Flyway.configure().dataSource(source).target("33").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("insert into user_account(id,email,role,name) values (1,'migration@example.com','STUDENT','학생')");
        var before = jdbc.queryForMap("select * from user_account where id=1");
        var flyway = Flyway.configure().dataSource(source).target("34").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1); flyway.validate();
        assertThat(jdbc.queryForMap("select * from user_account where id=1")).isEqualTo(before);
        var json = new JsonMapper().readTree(Files.readString(Path.of("docs/reference/accredited-universities-2026-11-23.json")));
        int total = 0;
        for (String category : java.util.List.of("GENERAL","JUNIOR","GRADUATE")) {
            var names = json.path("categories").path(category);
            assertThat(names.size()).isEqualTo(category.equals("GENERAL")?135:category.equals("JUNIOR")?33:16);
            for (var name : names) {
                assertThat(jdbc.queryForObject("select normalized_name from accredited_university_entry where effective_from='2026-11-23' and category=? and listed_name=?",String.class,category,name.asString()))
                    .isEqualTo(AccreditedUniversityService.normalize(name.asString()));
                total++;
            }
        }
        assertThat(total).isEqualTo(184);
        assertThat(jdbc.queryForObject("select count(*) from accredited_university_entry",Integer.class)).isEqualTo(184);
        assertThat(jdbc.queryForObject("select count(distinct normalized_name) from accredited_university_entry",Integer.class)).isEqualTo(173);
        assertThat(jdbc.queryForObject("select expected_entry_count from accredited_university_catalog where effective_from='2026-11-23'",Integer.class)).isEqualTo(184);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}
