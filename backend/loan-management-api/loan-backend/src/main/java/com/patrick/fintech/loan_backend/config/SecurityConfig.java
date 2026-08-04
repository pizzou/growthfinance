
package com.patrick.fintech.loan_backend.config;

import com.patrick.fintech.loan_backend.security.JwtAuthFilter;
import com.patrick.fintech.loan_backend.security.RateLimitFilter;
import com.patrick.fintech.loan_backend.security.RegulatoryApiKeyAuthFilter;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtFilter;
    private final RegulatoryApiKeyAuthFilter regulatoryApiKeyAuthFilter;
    private final RateLimitFilter rateLimitFilter;

    @Value("${app.cors.allowed-origins:https://growthfinance-six.vercel.app}")
    private String allowedOrigins;


    // ============================================================
    // SECURITY FILTER CHAIN
    // ============================================================

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http
    ) throws Exception {

        http

            // ====================================================
            // CORS
            // ====================================================

            .cors(cors ->
                cors.configurationSource(corsSource())
            )

            // ====================================================
            // CSRF
            // ====================================================

            .csrf(csrf ->
                csrf.disable()
            )

            // ====================================================
            // SESSION
            // ====================================================

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            // ====================================================
            // EXCEPTION HANDLING
            // ====================================================

            .exceptionHandling(exception ->
                exception

                    // ------------------------------------------------
                    // 401 - NOT AUTHENTICATED
                    // ------------------------------------------------

                    .authenticationEntryPoint(
                        (request, response, authException) -> {

                            response.setStatus(
                                jakarta.servlet.http.HttpServletResponse
                                    .SC_UNAUTHORIZED
                            );

                            response.setCharacterEncoding("UTF-8");

                            response.setContentType(
                                "application/json"
                            );

                            response.getWriter().write(
                                """
                                {
                                  "success": false,
                                  "status": 401,
                                  "error": "UNAUTHORIZED",
                                  "message": "Your session has expired or is no longer valid. Please log in again."
                                }
                                """
                            );
                        }
                    )

                    // ------------------------------------------------
                    // 403 - AUTHENTICATED BUT NOT AUTHORIZED
                    // ------------------------------------------------

                    .accessDeniedHandler(
                        (request, response, accessDeniedException) -> {

                            response.setStatus(
                                jakarta.servlet.http.HttpServletResponse
                                    .SC_FORBIDDEN
                            );

                            response.setCharacterEncoding("UTF-8");

                            response.setContentType(
                                "application/json"
                            );

                            response.getWriter().write(
                                """
                                {
                                  "success": false,
                                  "status": 403,
                                  "error": "FORBIDDEN",
                                  "message": "You are authenticated but do not have permission to perform this action."
                                }
                                """
                            );
                        }
                    )
            )

            // ====================================================
            // AUTHORIZATION
            // ====================================================

            .authorizeHttpRequests(authorize ->
                authorize

                    // ------------------------------------------------
                    // PUBLIC ENDPOINTS
                    // ------------------------------------------------

                    .requestMatchers(
                        "/api/auth/**",
                        "/h2-console/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/api-docs/**",
                        "/actuator/health",
                        "/api/public/**",
                        "/public/**"
                    )
                    .permitAll()

                    // ------------------------------------------------
                    // EVERYTHING ELSE REQUIRES AUTHENTICATION
                    // ------------------------------------------------

                    .anyRequest()
                    .authenticated()
            )

            // ====================================================
            // H2 CONSOLE
            // ====================================================

            .headers(headers ->
                headers.frameOptions(
                    frame -> frame.sameOrigin()
                )
            )

            // ====================================================
            // RATE LIMIT FILTER
            // ====================================================

            .addFilterBefore(
                rateLimitFilter,
                UsernamePasswordAuthenticationFilter.class
            )

            // ====================================================
            // JWT FILTER
            // ====================================================

            .addFilterBefore(
                jwtFilter,
                UsernamePasswordAuthenticationFilter.class
            )

            // ====================================================
            // REGULATORY API KEY FILTER
            // ====================================================

            .addFilterBefore(
                regulatoryApiKeyAuthFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }


    // ============================================================
    // CORS CONFIGURATION
    // ============================================================

    @Bean
    public CorsConfigurationSource corsSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();

        List<String> origins =
                Arrays.stream(
                        allowedOrigins.split(",")
                )
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();

        configuration.setAllowedOrigins(
                origins
        );

        configuration.setAllowedMethods(
                List.of(
                    "GET",
                    "POST",
                    "PUT",
                    "PATCH",
                    "DELETE",
                    "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of("*")
        );

        configuration.setExposedHeaders(
                List.of(
                    "Content-Disposition",
                    "Content-Type",
                    "X-Request-ID"
                )
        );

        configuration.setAllowCredentials(
                true
        );

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }


    // ============================================================
    // AUTHENTICATION MANAGER
    // ============================================================

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {

        return configuration.getAuthenticationManager();
    }
}