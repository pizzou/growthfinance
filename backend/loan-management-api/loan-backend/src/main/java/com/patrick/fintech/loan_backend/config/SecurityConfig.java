package com.patrick.fintech.loan_backend.config;

import com.patrick.fintech.loan_backend.security.JwtAuthFilter;
import com.patrick.fintech.loan_backend.security.RegulatoryApiKeyAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtFilter;
    private final RegulatoryApiKeyAuthFilter regulatoryApiKeyAuthFilter;

    @Value("${app.cors.allowed-origins:https://fintech01-cy17.vercel.app/}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(c -> c.configurationSource(corsSource()))
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint((request, response, authException) -> {
                response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write(
                    "{\"success\":false,\"error\":\"Your session has expired or is no longer valid. Please log in again.\"}");
            }))
            .authorizeHttpRequests(a -> a
                .requestMatchers(
                    "/api/auth/**",
                    "/h2-console/**",
                    "/swagger-ui/**", "/swagger-ui.html",
                    "/api-docs/**",
                    "/actuator/health",
                    "/api/public/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            // Only engages for /api/regulatory/external/** (see RegulatoryApiKeyAuthFilter's
            // shouldNotFilter) — everything else keeps authenticating via jwtFilter exactly as before.
            .addFilterBefore(regulatoryApiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }

    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}