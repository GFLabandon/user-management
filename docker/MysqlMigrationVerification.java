import java.sql.*;
import org.flywaydb.core.Flyway;

/** Runs only in the disposable migration-verification container, never the application. */
public final class MysqlMigrationVerification {
    private static final String URL = "jdbc:mysql://db:3306/counselor?sslMode=DISABLED&allowPublicKeyRetrieval=true";
    private static final String PASSWORD = System.getenv("DB_PASSWORD");
    private static Connection db;

    private static Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(URL, "counselor_app", PASSWORD)
                .locations("classpath:db/migration").cleanDisabled(true);
        if (target != null) config.target(target);
        return config.load();
    }
    private static void sql(String query) throws Exception {
        try (var statement = db.createStatement()) { statement.execute(query); }
    }
    private static String scalar(String query) throws Exception {
        try (var statement = db.createStatement(); var rows = statement.executeQuery(query)) {
            if (!rows.next()) throw new AssertionError("Missing expected row");
            return rows.getString(1);
        }
    }
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    private static void legacy() throws Exception {
        sql("INSERT INTO departments(id,name) VALUES (7,'旧院系')");
        sql("INSERT INTO roles(id,name) VALUES (9,'旧角色')");
        sql("INSERT INTO users(id,username,note,dept_id,photo_path) VALUES (42,'原始 姓名','原备注',7,'/uploads/legacy.png')");
        sql("INSERT INTO user_roles(user_id,role_id) VALUES (42,9)");
    }
    private static void expectFailure(String fragment) {
        try { flyway(null).migrate(); }
        catch (RuntimeException error) {
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause.getMessage() != null && cause.getMessage().contains(fragment)) return;
            }
            throw new AssertionError("Unexpected migration failure category");
        }
        throw new AssertionError("Migration should have failed");
    }
    public static void main(String[] args) throws Exception {
        try (var connection = DriverManager.getConnection(URL, "counselor_app", PASSWORD)) {
            db = connection;
            check(scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()").equals("0"), "Requires empty test schema");
            switch (args[0]) {
                case "fresh" -> {
                    check(flyway(null).migrate().migrationsExecuted == 4, "Expected V1-V4");
                    check(scalar("SELECT COUNT(*) FROM counselors").equals("0"), "No implicit demo data");
                    check(scalar("SELECT COUNT(*) FROM system_accounts").equals("0"), "No implicit accounts");
                    check(flyway(null).migrate().migrationsExecuted == 0, "Repeat migration");
                }
                case "missing-mapping" -> {
                    flyway("2").migrate(); legacy();
                    // One mapped row followed by an unmapped row must not be copied partially.
                    sql("INSERT INTO legacy_counselor_mapping VALUES (42,'CONFIRMED-42')");
                    sql("INSERT INTO users(id,username,note,dept_id) VALUES (43,'缺少工号','原备注',7)");
                    expectFailure("requires a unique, uppercase employee number");
                    check(scalar("SELECT COUNT(*) FROM counselors").equals("0"), "No partial copy");
                    check(scalar("SELECT COUNT(*) FROM counselor_status_history").equals("0"), "No partial history");
                    check(scalar("SELECT COUNT(*) FROM users").equals("2"), "Legacy data retained");
                    check(scalar("SELECT COUNT(*) FROM user_roles").equals("1"), "Legacy relationship retained");
                    check(scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IN ('3','4')").equals("0"), "Failed migration not successful");
                    // Do not repair or retry this failed schema; dispose of this test project.
                }
                case "mapped-legacy" -> {
                    flyway("2").migrate(); legacy();
                    sql("INSERT INTO legacy_counselor_mapping VALUES (42,'CONFIRMED-42')");
                    check(flyway(null).migrate().migrationsExecuted == 2, "Upgrade V2 to V4");
                    check(scalar("SELECT CONCAT(employee_no,'|',name,'|',department_id,'|',photo_path,'|',remark) FROM counselors WHERE id=42")
                            .equals("CONFIRMED-42|原始 姓名|7|/uploads/legacy.png|原备注"), "Identity and photo preserved");
                    check(scalar("SELECT COUNT(*) FROM user_roles WHERE user_id=42 AND role_id=9").equals("1"), "Roles retained");
                    check(scalar("SELECT actor FROM counselor_status_history WHERE counselor_id=42").equals("legacy-migration"), "Migration history");
                    sql("INSERT INTO counselors(employee_no,name,department_id,employment_status) VALUES ('NEW-43','新档案',7,'ACTIVE')");
                    check(Integer.parseInt(scalar("SELECT id FROM counselors WHERE employee_no='NEW-43'")) > 42, "Auto increment advanced");
                    check(flyway(null).migrate().migrationsExecuted == 0, "Repeat upgrade");
                }
                case "v3-upgrade" -> {
                    flyway("3").migrate();
                    sql("INSERT INTO departments(id,name) VALUES (7,'保留院系')");
                    sql("INSERT INTO counselors(id,employee_no,name,department_id,employment_status,photo_path) VALUES (42,'KEEP-42','保留档案',7,'ACTIVE','/uploads/kept.png')");
                    check(flyway(null).migrate().migrationsExecuted == 1, "Upgrade only V4");
                    check(scalar("SELECT photo_path FROM counselors WHERE id=42").equals("/uploads/kept.png"), "V3 record retained");
                    check(scalar("SELECT COUNT(*) FROM system_accounts").equals("0"), "Bootstrap still explicit");
                    check(scalar("SELECT COUNT(*) FROM account_admin_guard").equals("1"), "Admin guard initialized");
                    check(scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1").equals("4"), "Complete migration history");
                }
                case "unversioned" -> {
                    sql("CREATE TABLE existing_private_data(id INT PRIMARY KEY)");
                    sql("INSERT INTO existing_private_data VALUES (7)");
                    expectFailure("non-empty schema");
                    check(scalar("SELECT id FROM existing_private_data").equals("7"), "No implicit baseline or data deletion");
                }
                default -> throw new IllegalArgumentException("Unknown scenario");
            }
            System.out.println("PASS " + args[0]);
        }
    }
}
