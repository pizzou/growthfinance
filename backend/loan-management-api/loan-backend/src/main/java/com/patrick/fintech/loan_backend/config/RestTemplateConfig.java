package com.patrick.fintech.loan_backend.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Value("${app.credit-bureau.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${app.credit-bureau.read-timeout-ms:15000}")
    private int readTimeoutMs;

    @Bean
    public RestTemplate restTemplate(
            RestTemplateBuilder builder
    ) {
        return builder
                .setConnectTimeout(
                        Duration.ofMillis(connectTimeoutMs)
                )
                .setReadTimeout(
                        Duration.ofMillis(readTimeoutMs)
                )
                .build();
    }
}