package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.util.Optional;

@Service @RequiredArgsConstructor @Slf4j
public class IdempotencyService {
    private final IdempotencyKeyRepository repo;
    private final ObjectMapper objectMapper;

    @Transactional
    public IdempotencyOutcome checkOrReserve(String key, Organization org, String endpoint, String requestBody) {
        if (key==null||key.isBlank()) return IdempotencyOutcome.proceed();
        String hash = sha256(requestBody==null?"":requestBody);
        Optional<IdempotencyKey> existing = repo.findByKeyAndOrganization(key,org);
        if (existing.isPresent()) {
            IdempotencyKey rec = existing.get();
            if (!rec.getRequestHash().equals(hash))
                throw new RuntimeException("Idempotency-Key '"+key+"' reused with different request body");
            if (rec.getStatus()==IdempotencyKey.Status.COMPLETED) {
                log.info("Idempotent replay for key {}",key);
                return IdempotencyOutcome.replay(rec.getResponseBody(),rec.getResponseStatusCode());
            }
            throw new RuntimeException("Request with Idempotency-Key '"+key+"' is already in progress");
        }
        repo.save(IdempotencyKey.builder().key(key).organization(org)
            .endpoint(endpoint).requestHash(hash).status(IdempotencyKey.Status.IN_PROGRESS).build());
        return IdempotencyOutcome.proceed();
    }

    @Transactional
    public void recordSuccess(String key, Organization org, Object responseBody, int statusCode) {
        if (key==null||key.isBlank()) return;
        repo.findByKeyAndOrganization(key,org).ifPresent(rec->{
            try { rec.setResponseBody(objectMapper.writeValueAsString(responseBody)); } catch(Exception e){rec.setResponseBody("{}");}
            rec.setResponseStatusCode(statusCode);
            rec.setStatus(IdempotencyKey.Status.COMPLETED);
            repo.save(rec);
        });
    }

    @Transactional
    public void recordFailure(String key, Organization org) {
        if (key==null||key.isBlank()) return;
        repo.findByKeyAndOrganization(key,org).ifPresent(repo::delete);
    }

    private String sha256(String input) {
        try {
            MessageDigest d=MessageDigest.getInstance("SHA-256");
            byte[]h=d.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb=new StringBuilder();
            for(byte b:h) sb.append(String.format("%02x",b));
            return sb.toString();
        } catch(Exception e){return input;}
    }

    public record IdempotencyOutcome(boolean isReplay,boolean shouldProceed,String cachedResponseBody,Integer cachedStatusCode) {
        static IdempotencyOutcome proceed() { return new IdempotencyOutcome(false,true,null,null); }
        static IdempotencyOutcome replay(String body,Integer status) { return new IdempotencyOutcome(true,false,body,status); }
    }
}
