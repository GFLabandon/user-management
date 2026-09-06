package io.github.gflabandon.counselor.security;

import java.util.List;
import io.github.gflabandon.counselor.entity.SystemAccount;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class AccountPrincipal extends User {
    private static final long serialVersionUID = 1L;
    private final int accountId;
    private final int accountVersion;
    public AccountPrincipal(SystemAccount account) {
        super(account.getUsername(), account.getPasswordHash(), account.getEnabled(), true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())));
        this.accountId = account.getId();
        this.accountVersion = account.getVersion();
    }
    public int getAccountId() { return accountId; }
    public int getAccountVersion() { return accountVersion; }
}
