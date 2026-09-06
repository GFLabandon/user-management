package io.github.gflabandon.counselor.mapper;

import java.util.List;
import io.github.gflabandon.counselor.entity.SystemAccount;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AccountMapper {
    @Select("SELECT * FROM system_accounts WHERE username = #{username}")
    SystemAccount findByUsername(String username);
    @Select("SELECT * FROM system_accounts WHERE id = #{id}")
    SystemAccount findById(int id);
    @Select("SELECT * FROM system_accounts ORDER BY id")
    List<SystemAccount> all();
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
