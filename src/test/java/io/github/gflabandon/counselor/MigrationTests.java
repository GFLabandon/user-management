package io.github.gflabandon.counselor;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class MigrationTests {
    private String database() { return "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"; }
    private JdbcTemplate jdbc(String url) { return new JdbcTemplate(new DriverManagerDataSource(url, "sa", "")); }
    private Flyway flyway(String url, String target) {
        var config = Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration");
        if (target != null) config.target(target);
        return config.load();
    }
    private void legacyData(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO departments(id,name) VALUES (7,'旧院系')");
        jdbc.update("INSERT INTO roles(id,name) VALUES (9,'旧角色')");
        jdbc.update("INSERT INTO users(id,username,note,dept_id,photo_path) VALUES (42,'原始 姓名','原备注',7,'/uploads/legacy.png')");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (42,9)");
    }

    @Test void freshSchemaHasNoImplicitDemoDataAndMigrationIsRepeatable() {
        String url=database(); Flyway flyway=flyway(url,null); flyway.migrate();
        assertThat(jdbc(url).queryForObject("SELECT COUNT(*) FROM counselors",Integer.class)).isZero();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test void missingMappingFailsBeforeCopyingAnyLegacyRows() {
        String url=database(); flyway(url,"2").migrate(); JdbcTemplate jdbc=jdbc(url); legacyData(jdbc);
        assertThatThrownBy(() -> flyway(url,null).migrate()).hasStackTraceContaining("requires a unique, uppercase employee number");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM counselors",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_roles",Integer.class)).isEqualTo(1);
    }

    @Test void mappedLegacyCopyPreservesIdentityRelationshipsImageAndAdvancesSequence() {
        String url=database(); flyway(url,"2").migrate(); JdbcTemplate jdbc=jdbc(url); legacyData(jdbc);
        jdbc.update("INSERT INTO legacy_counselor_mapping VALUES (42,'CONFIRMED-42')");
        flyway(url,null).migrate();
        var row=jdbc.queryForMap("SELECT * FROM counselors WHERE id=42");
        assertThat(row).containsEntry("name","原始 姓名").containsEntry("employee_no","CONFIRMED-42")
                .containsEntry("department_id",7).containsEntry("photo_path","/uploads/legacy.png").containsEntry("remark","原备注");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_roles",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT actor FROM counselor_status_history",String.class)).isEqualTo("legacy-migration");
        jdbc.update("INSERT INTO counselors(employee_no,name,department_id,employment_status) VALUES ('NEW-43','同名允许',7,'ACTIVE')");
        assertThat(jdbc.queryForObject("SELECT id FROM counselors WHERE employee_no='NEW-43'",Integer.class)).isGreaterThan(42);
        assertThat(flyway(url,null).migrate().migrationsExecuted).isZero();
    }

    @Test void unversionedDatabaseRequiresExplicitBaseline() {
        String url=database(); JdbcTemplate jdbc=jdbc(url);
        jdbc.execute("CREATE TABLE existing_private_data(id INT)");
        assertThatThrownBy(() -> flyway(url,null).migrate()).hasStackTraceContaining("non-empty schema");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM existing_private_data",Integer.class)).isZero();
    }
}
