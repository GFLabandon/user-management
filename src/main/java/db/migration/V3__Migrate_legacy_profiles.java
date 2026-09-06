package db.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Copies only explicitly mapped legacy records. Original tables and roles remain untouched. */
public class V3__Migrate_legacy_profiles extends BaseJavaMigration {
    @Override
    public Integer getChecksum() { return 1; }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        // Validate all rows before any inserts, including on MySQL's non-transactional DDL path.
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT u.id, m.employee_no FROM users u
                     LEFT JOIN legacy_counselor_mapping m ON m.legacy_user_id = u.id
                     ORDER BY u.id
                     """)) {
            Set<String> numbers = new HashSet<>();
            while (rows.next()) {
                String number = rows.getString("employee_no");
                if (number == null || !number.matches("[A-Z0-9][A-Z0-9_-]{1,31}") || !numbers.add(number)) {
                    throw new IllegalStateException("Legacy user id " + rows.getInt("id")
                            + " requires a unique, uppercase employee number in legacy_counselor_mapping; "
                            + "restore the rehearsal backup and complete the mapping before V3.");
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO counselors (id, employee_no, name, department_id, employment_status, photo_path, remark)
                    SELECT u.id, m.employee_no, u.username, u.dept_id, 'ACTIVE', u.photo_path, u.note
                    FROM users u JOIN legacy_counselor_mapping m ON m.legacy_user_id = u.id
                    ORDER BY u.id
                    """);
            statement.executeUpdate("""
                    INSERT INTO counselor_status_history (counselor_id, from_status, to_status, actor)
                    SELECT id, NULL, 'ACTIVE', 'legacy-migration' FROM counselors
                    """);
        }
        // MySQL advances AUTO_INCREMENT for explicit ids; H2 also needs an explicit restart.
        if (connection.getMetaData().getDatabaseProductName().equals("H2")) {
            long next;
            try (Statement statement = connection.createStatement();
                 ResultSet row = statement.executeQuery("SELECT COALESCE(MAX(id), 0) + 1 FROM counselors")) {
                row.next(); next = row.getLong(1);
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE counselors ALTER COLUMN id RESTART WITH " + next);
            }
        }
    }
}
