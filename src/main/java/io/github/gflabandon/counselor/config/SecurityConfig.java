package io.github.gflabandon.counselor.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfException;
import io.github.gflabandon.counselor.security.LiveAccountFilter;
import io.github.gflabandon.counselor.mapper.AccountMapper;
import io.github.gflabandon.counselor.service.AuditService;

@Configuration
@ConditionalOnWebApplication
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccountMapper accounts, AuditService audit) throws Exception {
        var denied = (org.springframework.security.web.access.AccessDeniedHandler) (request, response, exception) -> {
            audit.event(AuditService.actor(), "ACCESS_DENIED", "REQUEST", null, "DENIED",
                    exception instanceof CsrfException ? "CSRF" : "ROLE");
            response.sendError(403);
        };
        http.addFilterAfter(new LiveAccountFilter(accounts), SecurityContextHolderFilter.class)
            .authorizeHttpRequests(r -> r
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/login", "/css/**", "/js/**", "/favicon.ico", "/error").permitAll()
                .requestMatchers("/accounts", "/accounts/**", "/audit").hasRole("ADMIN")
                .requestMatchers("/counselors/new", "/counselors/*/edit", "/departments/new", "/departments/*/edit").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/", "/counselors", "/counselors/**", "/departments", "/uploads/*", "/users", "/users/list").hasAnyRole("ADMIN", "VIEWER")
                .requestMatchers(HttpMethod.HEAD, "/uploads/*").hasAnyRole("ADMIN", "VIEWER")
                .requestMatchers(HttpMethod.POST, "/counselors", "/counselors/**", "/departments", "/departments/**").hasRole("ADMIN")
                .anyRequest().denyAll())
            .exceptionHandling(e -> e.accessDeniedHandler(denied))
            .formLogin(f -> f.loginPage("/login").successHandler((request, response, authentication) -> {
                try { audit.event(authentication.getName(), "LOGIN", "ACCOUNT", null, "SUCCESS", "OK"); }
                catch (RuntimeException failure) {
                    if (request.getSession(false) != null) request.getSession(false).invalidate();
                    SecurityContextHolder.clearContext(); response.sendError(503); return;
                }
                response.sendRedirect(request.getContextPath() + "/counselors");
            }).failureHandler((request, response, exception) -> {
                audit.event("anonymous", "LOGIN", "ACCOUNT", null, "FAILURE", "INVALID_CREDENTIALS");
                response.sendRedirect(request.getContextPath() + "/login?error");
            }).permitAll())
            .logout(l -> l.logoutSuccessUrl("/login?logout"));
        return http.build();
    }
}
