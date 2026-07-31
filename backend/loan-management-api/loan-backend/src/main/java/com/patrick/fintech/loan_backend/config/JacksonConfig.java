package com.patrick.fintech.loan_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson configuration to prevent ByteBuddyInterceptor serialization errors.
 * We do NOT use Hibernate6Module here because it requires an extra dependency
 * that conflicts on some Spring Boot 3.x setups.
 * Instead we use @JsonIgnoreProperties on all entities (already done)
 * and configure ObjectMapper to not fail on empty beans.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }
}
