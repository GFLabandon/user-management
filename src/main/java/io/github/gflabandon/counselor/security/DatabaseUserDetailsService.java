package io.github.gflabandon.counselor.security;

import java.util.Locale;
import io.github.gflabandon.counselor.entity.SystemAccount;
import io.github.gflabandon.counselor.mapper.AccountMapper;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final AccountMapper mapper;
    public DatabaseUserDetailsService(AccountMapper mapper) { this.mapper = mapper; }
    @Override public UserDetails loadUserByUsername(String username) {
        SystemAccount account = mapper.findByUsername(username.trim().toLowerCase(Locale.ROOT));
        if (account == null) throw new UsernameNotFoundException("Invalid credentials");
        return new AccountPrincipal(account);
    }
}
