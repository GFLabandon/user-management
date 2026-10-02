package io.github.gflabandon.counselor.mapper;

import java.util.List;
import io.github.gflabandon.counselor.entity.SystemAccount;
import io.github.gflabandon.counselor.service.AccountService.AccountSummary;
import io.github.gflabandon.counselor.web.CounselorOption;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AccountMapper {
    @Select("SELECT * FROM system_accounts WHERE username = #{username}")
    SystemAccount findByUsername(String username);
    @Select("SELECT * FROM system_accounts WHERE id = #{id}")
    SystemAccount findById(int id);
    @Select("SELECT * FROM system_accounts ORDER BY id")
    List<SystemAccount> all();
    @Select("""
            SELECT a.id, a.username, a.role, a.enabled, a.counselor_id,
                   c.name AS counselor_name, c.employee_no, d.name AS department_name
            FROM system_accounts a LEFT JOIN counselors c ON c.id = a.counselor_id
            LEFT JOIN departments d ON d.id = c.department_id ORDER BY a.id
            """)
    List<AccountSummary> summaries();
    @Select("""
            SELECT c.id, c.employee_no, c.name, d.name AS department_name
            FROM counselors c JOIN departments d ON d.id = c.department_id
            WHERE (LOCATE(LOWER(#{keyword}), LOWER(c.employee_no)) > 0
                OR LOCATE(LOWER(#{keyword}), LOWER(c.name)) > 0)
              AND NOT EXISTS (SELECT 1 FROM system_accounts a WHERE a.counselor_id = c.id
                  AND (#{accountId,jdbcType=INTEGER} IS NULL OR a.id != #{accountId,jdbcType=INTEGER}))
            ORDER BY c.employee_no, c.id LIMIT 20
            """)
    List<CounselorOption> counselorOptions(@Param("keyword") String keyword, @Param("accountId") Integer accountId);
    @Select("""
            SELECT c.id, c.employee_no, c.name, d.name AS department_name
            FROM counselors c JOIN departments d ON d.id = c.department_id WHERE c.id = #{id}
            """)
    CounselorOption counselorOption(int id);
    @Select("SELECT id FROM system_accounts WHERE counselor_id = #{id}")
    Integer linkedAccount(int id);
    @Select("SELECT COUNT(*) FROM system_accounts")
    int count();
    @Select("SELECT COUNT(*) FROM system_accounts WHERE role = 'ADMIN' AND enabled = TRUE")
    int activeAdmins();
    @Select("SELECT id FROM account_admin_guard WHERE id = 1 FOR UPDATE")
    int lockAdministration();
    @Insert("""
            INSERT INTO system_accounts (username, password_hash, role, enabled, counselor_id)
            VALUES (#{username}, #{passwordHash}, #{role}, #{enabled}, #{counselorId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(SystemAccount account);
    @Update("""
            UPDATE system_accounts SET password_hash = #{passwordHash}, role = #{role}, enabled = #{enabled},
                counselor_id = #{counselorId}, version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND version = #{version}
            """)
    int update(SystemAccount account);
}
