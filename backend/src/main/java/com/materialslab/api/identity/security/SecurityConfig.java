package com.materialslab.api.identity.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/** 配置同源 Session、CSRF 与 JSON 异常响应。 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
                                                   DatabaseUserDetailsService userDetailsService) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookieName("csrftoken");
        csrf.setHeaderName("X-CSRFToken");
        http.csrf(configurer -> configurer.csrfTokenRepository(csrf)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/api/v1/health/**", "/api/v1/auth/csrf"))
                .sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers("/api/v1/health/**", "/api/v1/auth/csrf", "/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(configurer -> configurer.disable())
                .formLogin(configurer -> configurer.disable())
                .logout(configurer -> configurer.disable())
                .exceptionHandling(configurer -> configurer
                        .authenticationEntryPoint((request, response, exception) -> writeError(response, objectMapper, 401, "authentication_required"))
                        .accessDeniedHandler((request, response, exception) -> writeError(response, objectMapper, 403, "permission_denied")));
        http.addFilterAfter(new SessionPrincipalRefreshFilter(userDetailsService, objectMapper), SecurityContextHolderFilter.class);
        return http.build();
    }

    static void writeError(HttpServletResponse response, ObjectMapper objectMapper, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of("status", status, "code", code, "detail", "认证或权限校验失败"));
    }
}
