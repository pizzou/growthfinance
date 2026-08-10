package com.patrick.fintech.loan_backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;

import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;

import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import java.util.concurrent.Executor;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AppConfig implements AsyncConfigurer {

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        var reg = new FilterRegistrationBean<RequestIdFilter>(
                new RequestIdFilter()
        );

        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/*");

        return reg;
    }

    /**
     * Async executor used by @Async services such as:
     * - audit logging
     * - SMS notifications
     * - email notifications
     * - webhook dispatch
     * - other background tasks
     */
    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix(
                "loansaas-async-"
        );

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);

        executor.initialize();

        return executor;
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title(
                                        "LoanSaaS Pro — Enterprise Loan Management API"
                                )
                                .version("2.0.0")
                                .description(
                                        "Multi-tenant, international-grade loan management platform.\n\n"
                                                + "Features: multi-org isolation, 12 loan types, FX support, "
                                                + "webhook events, audit logs, Flutterwave payments, risk scoring."
                                )
                                .contact(
                                        new Contact()
                                                .name("Support")
                                                .email("support@loansaas.io")
                                )
                )
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList("Bearer Auth")
                )
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "Bearer Auth",
                                        new SecurityScheme()
                                                .type(
                                                        SecurityScheme.Type.HTTP
                                                )
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                )
                );
    }
}