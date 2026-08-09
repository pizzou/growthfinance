package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Records who did what, from where.
 *
 * Every audit entry captures: the acting user (auto-resolved from the
 * security context when the caller doesn't have one on hand — e.g. public,
 * unauthenticated endpoints correctly leave this null), IP address,
 * operating system + browser (parsed from the User-Agent), and a best-effort
 * geolocation of the IP. Capture of the user/IP/UA happens synchronously on
 * the calling (request) thread — where ThreadLocal security/request context
 * is actually available — before the slower work (geolocation + DB write)
 * is handed off asynchronously.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final UserRepository     userRepository;
    private final AuditPersistenceService persistenceService;

    // ---- Public API (unchanged signatures — all existing call sites keep working) ----

    public void log(Organization org, User user, String action,
                     String entityType, String entityId, String description) {
        log(org, user, action, entityType, entityId, description, null, null);
    }

    public void log(Organization org, User user, String action,
                     String entityType, String entityId, String description,
                     String before, String after) {
        log(org, user, action, entityType, entityId, description, before, after, null);
    }

    /** Same as above, but lets the caller pin an exact module/page name (e.g. "Authentication")
     *  instead of relying on the entityType -> module derivation in AuditPersistenceService. */
    public void log(Organization org, User user, String action,
                     String entityType, String entityId, String description,
                     String before, String after, String module) {
        User actor = user != null ? user : safeResolveCurrentUser();
        String ip  = extractIp();
        String ua  = extractUserAgent();
        // IMPORTANT: pass IDs, not the entity objects themselves, across the @Async
        // boundary. org/actor were loaded in THIS request's Hibernate session, which
        // closes when this request returns — often before the async task even runs.
        // A save() referencing that now-detached entity can silently fail to persist
        // the association (the row gets written, but the foreign key ends up null),
        // which is exactly why some audit entries were showing "System / Public" for
        // actions a real logged-in user actually performed. Passing IDs and re-fetching
        // fresh inside the async method's own session avoids this entirely.
        Long orgId   = org   != null ? org.getId()   : null;
        Long actorId = actor != null ? actor.getId() : null;
        persistenceService.persist(orgId, actorId, action, entityType, entityId, description, before, after, ip, ua, module);
    }

    // ---- Synchronous capture (must run on the calling/request thread, where
    //      SecurityContextHolder / RequestContextHolder ThreadLocals are populated) ----

    /** Best-effort — returns null in any context without an authenticated request (public endpoints, scheduled jobs, seeders). */
    private User safeResolveCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
            return userRepository.findByEmail(auth.getName()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractIp() {
        HttpServletRequest req = currentRequest();
        if (req == null) return null;
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String realIp = req.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return req.getRemoteAddr();
    }

    private String extractUserAgent() {
        HttpServletRequest req = currentRequest();
        return req != null ? req.getHeader("User-Agent") : null;
    }
}
