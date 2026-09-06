package io.github.gflabandon.counselor.service;

import java.util.Locale;
import io.github.gflabandon.counselor.entity.*;
import io.github.gflabandon.counselor.mapper.AccountMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnWebApplication
public class AccountBootstrap implements ApplicationRunner {
    private final AccountMapper mapper;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final String username;
    private final String password;
    private final boolean demo;
    public AccountBootstrap(AccountMapper mapper, PasswordEncoder encoder, AuditService audit,
                            @Value("${app.bootstrap.username:}") String username,
                            @Value("${app.bootstrap.password:}") String password,
                            @Value("${app.demo:false}") boolean demo) {
        this.mapper = mapper; this.encoder = encoder; this.audit = audit;
        this.username = username; this.password = password; this.demo = demo;
    }
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        mapper.lockAdministration();
        if (mapper.count() > 0) {
            if (mapper.activeAdmins() == 0) throw new IllegalStateException("No enabled administrator; restore a valid account backup.");
            return;
        }
        if (!username.matches("[A-Za-z0-9][A-Za-z0-9._-]{2,49}")) {
            throw new IllegalStateException("Empty account database requires APP_BOOTSTRAP_USERNAME and APP_BOOTSTRAP_PASSWORD.");
        }
        AccountService.validatePassword(password);
        if (demo && username.equalsIgnoreCase("viewer")) throw new IllegalStateException("Demo administrator must differ from viewer.");
        create(username.toLowerCase(Locale.ROOT), password, AccountRole.ADMIN);
        if (demo) create("viewer", "demo-viewer-pass", AccountRole.VIEWER);
    }
    private void create(String username, String password, AccountRole role) {
        SystemAccount account = new SystemAccount();
        account.setUsername(username); account.setPasswordHash(encoder.encode(password));
        account.setRole(role); account.setEnabled(true); mapper.insert(account);
        audit.success("bootstrap", "ACCOUNT_CREATE", "ACCOUNT", account.getId());
    }
}
