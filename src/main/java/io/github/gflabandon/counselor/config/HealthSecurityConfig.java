package io.github.gflabandon.counselor.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Probes never read a session or query account state, including during a database outage. */
@Configuration
@ConditionalOnWebApplication
public class HealthSecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain healthSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/actuator", "/actuator/**")
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable())
                .authorizeHttpRequests(r -> r
                        .requestMatchers(HttpMethod.GET, "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, exception) -> response.setStatus(403))
                        .accessDeniedHandler((request, response, exception) -> response.setStatus(403)));
        return http.build();
    }
}
