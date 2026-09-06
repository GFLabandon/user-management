package io.github.gflabandon.counselor.security;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import io.github.gflabandon.counselor.mapper.AccountMapper;
import io.github.gflabandon.counselor.entity.SystemAccount;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class LiveAccountFilter extends OncePerRequestFilter {
    private final AccountMapper accounts;
    public LiveAccountFilter(AccountMapper accounts) { this.accounts = accounts; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            SystemAccount current = auth.getPrincipal() instanceof AccountPrincipal principal
                    ? accounts.findById(principal.getAccountId()) : null;
            if (current == null || !current.getEnabled()
                    || current.getVersion() != ((AccountPrincipal) auth.getPrincipal()).getAccountVersion()) {
                if (request.getSession(false) != null) request.getSession(false).invalidate();
                SecurityContextHolder.clearContext();
                response.sendRedirect(request.getContextPath() + "/login?expired");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
