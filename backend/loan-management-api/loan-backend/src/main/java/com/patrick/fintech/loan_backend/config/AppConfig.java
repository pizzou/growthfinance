package com.patrick.fintech.loan_backend.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;
import java.util.concurrent.Executor;

@Configuration
public class AppConfig implements AsyncConfigurer {

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        var reg = new org.springframework.boot.web.servlet.FilterRegistrationBean<RequestIdFilter>(new RequestIdFilter());
        reg.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/*");
        return reg;
    }

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(8000);
        return new RestTemplate(factory);
    }

    /**
     * Without this, Spring's default @Async behavior spins up a brand-new OS
     * thread for every single async call (every audit log write, SMS,
     * notification, webhook dispatch) instead of reusing a pool — expensive
     * under any real load and a common cause of the app feeling sluggish
     * under concurrent use.
     */
    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("loansaas-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("LoanSaaS Pro — Enterprise Loan Management API")
                .version("2.0.0")
                .description("Multi-tenant, international-grade loan management platform.\n\n" +
                    "Features: multi-org isolation, 12 loan types, FX support, webhook events, " +
                    "audit logs, Flutterwave payments, risk scoring.")
                .contact(new Contact().name("Support").email("support@loansaas.io")))
            .addSecurityItem(new SecurityRequirement().addList("Bearer Auth"))
            .components(new Components().addSecuritySchemes("Bearer Auth",
                new SecurityScheme().type(SecurityScheme.Type.HTTP)
                    .scheme("bearer").bearerFormat("JWT")));
    }
}
