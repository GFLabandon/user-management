package io.github.gflabandon.counselor.service;

import static org.assertj.core.api.Assertions.*;
import io.github.gflabandon.counselor.mapper.AccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AccountBootstrapTests {
    @Autowired AccountMapper accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired AuditService audit;
    @Autowired JdbcTemplate jdbc;
    private void bootstrap(String name, String password) { new AccountBootstrap(accounts, encoder, audit, name, password, false).run(new DefaultApplicationArguments()); }
    @Test void emptyDatabaseRequiresCredentialsAndRejectsDemoPasswords() {
        jdbc.update("DELETE FROM system_accounts");
        assertThatThrownBy(() -> bootstrap("", "")).hasMessageContaining("APP_BOOTSTRAP");
        assertThatThrownBy(() -> bootstrap("school-admin", "")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> bootstrap("school-admin", "demo-admin-pass")).hasMessageContaining("unique administrator");
        assertThatThrownBy(() -> bootstrap("school-admin", "replace-with-a-unique-password")).hasMessageContaining("unique administrator");
        assertThat(accounts.count()).isZero();
    }
    @Test void freshBootstrapHashesPasswordAndExistingAccountsNeverReset() {
        jdbc.update("DELETE FROM system_accounts");
        bootstrap("School-Admin", "fresh-local-admin-pass");
        var before=accounts.findByUsername("school-admin");
        assertThat(accounts.count()).isEqualTo(1);
        assertThat(before.getPasswordHash()).startsWith("{bcrypt}");
        assertThat(encoder.matches("fresh-local-admin-pass",before.getPasswordHash())).isTrue();
        bootstrap("", ""); bootstrap("replacement", "demo-admin-pass");
        var after=accounts.findByUsername("school-admin");
        assertThat(accounts.count()).isEqualTo(1);
        assertThat(after.getVersion()).isEqualTo(before.getVersion());
        assertThat(after.getPasswordHash()).isEqualTo(before.getPasswordHash());
    }
    @Test void existingDatabaseWithoutEnabledAdministratorFailsInsteadOfCreatingOne() {
        jdbc.update("UPDATE system_accounts SET enabled = FALSE");
        int before=accounts.count();
        assertThatThrownBy(() -> bootstrap("recovery-admin", "fresh-local-admin-pass")).hasMessageContaining("No enabled administrator");
        assertThat(accounts.count()).isEqualTo(before);
    }
}
