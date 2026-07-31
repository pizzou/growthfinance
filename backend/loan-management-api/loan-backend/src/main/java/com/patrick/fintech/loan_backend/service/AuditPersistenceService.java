package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.AuditLog;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.AuditLogRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Does the slow part of writing an audit entry — User-Agent parsing and a
 * best-effort IP geolocation lookup — off the request thread. Kept as a
 * separate bean (rather than a method on AuditService) so that Spring's
 * @Async proxy actually applies: an @Async method only runs asynchronously
 * when it's invoked *through the proxy*, i.e. called from another bean —
 * calling it on `this` from within the same class silently runs it inline.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditPersistenceService {

    private final AuditLogRepository  auditLogRepo;
    private final OrganizationRepository orgRepo;
    private final UserRepository      userRepo;

    /** Short-timeout client dedicated to geolocation lookups so a slow/unreachable
     *  geo provider can never back up the shared application RestTemplate or the async pool. */
    private final RestTemplate geoClient = buildGeoClient();

    private static final Map<String, String> GEO_CACHE = new ConcurrentHashMap<>();

    // Both overloads are @Async entry points in their own right — per the class javadoc,
    // one calling the other via `this` would bypass the proxy and run inline, so instead
    // each is annotated separately and both delegate to the private, non-async doPersist().
    @Async
    public void persist(Long orgId, Long actorId, String action, String entityType, String entityId,
                         String description, String before, String after, String ip, String ua) {
        doPersist(orgId, actorId, action, entityType, entityId, description, before, after, ip, ua, null);
    }

    @Async
    public void persist(Long orgId, Long actorId, String action, String entityType, String entityId,
                         String description, String before, String after, String ip, String ua,
                         String moduleOverride) {
        doPersist(orgId, actorId, action, entityType, entityId, description, before, after, ip, ua, moduleOverride);
    }

    private void doPersist(Long orgId, Long actorId, String action, String entityType, String entityId,
                         String description, String before, String after, String ip, String ua,
                         String moduleOverride) {
        try {
            // Re-fetch fresh here, in this method's own transaction/session, rather than
            // using the Organization/User objects loaded on the original request thread —
            // those became detached the moment that request finished, and saving a new
            // entity referencing a detached one can silently drop the association instead
            // of throwing, which is exactly what caused some entries to show no user.
            Organization org   = orgId   != null ? orgRepo.findById(orgId).orElse(null)   : null;
            User         actor = actorId != null ? userRepo.findById(actorId).orElse(null) : null;

            String previousHash = auditLogRepo.findTopByOrderByIdDesc()
                .map(AuditLog::getEntryHash).orElse("GENESIS");

            String os = parseOs(ua);
            String browser = parseBrowser(ua);
            String location = geolocate(ip);
            String module = (moduleOverride != null && !moduleOverride.isBlank())
                ? moduleOverride : deriveModule(entityType);
            String timestamp = java.time.LocalDateTime.now().toString();

            String entryHash = sha256(String.join("|",
                previousHash,
                org != null ? String.valueOf(org.getId()) : "",
                actor != null ? String.valueOf(actor.getId()) : "",
                action, entityType, entityId != null ? entityId : "",
                description != null ? description : "", timestamp));

            auditLogRepo.save(AuditLog.builder()
                .organization(org).user(actor).action(action)
                .entityType(entityType).entityId(entityId)
                .description(description).beforeValue(before).afterValue(after)
                .ipAddress(ip).userAgent(ua)
                .operatingSystem(os).browser(browser)
                .location(location).module(module)
                .previousHash(previousHash).entryHash(entryHash)
                .build());
        } catch (Exception e) {
            log.warn("Audit log failed: {}", e.getMessage());
        }
    }

    private String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // ---- entityType -> human-readable module/page, kept in sync with V19 migration's backfill ----

    private static final Map<String, String> MODULE_BY_ENTITY_TYPE = Map.ofEntries(
        Map.entry("LOAN", "Loans"),
        Map.entry("LOAN_PRODUCT", "Loan Products"),
        Map.entry("PAYMENT", "Payments"),
        Map.entry("BORROWER", "Borrowers & KYC"),
        Map.entry("BORROWER_FILE", "Documents & KYC"),
        Map.entry("BRANCH", "Branches"),
        Map.entry("ORGANIZATION", "Organization Settings"),
        Map.entry("COLLECTION_CASE", "Collections"),
        Map.entry("BULK_DISBURSEMENT", "Bulk Disbursement"),
        Map.entry("USER", "User Management"),
        Map.entry("ROLE", "Roles & Permissions"),
        Map.entry("WEBHOOK", "Webhooks & Integrations"),
        Map.entry("AUTH", "Authentication")
    );

    private String deriveModule(String entityType) {
        if (entityType == null) return "General";
        return MODULE_BY_ENTITY_TYPE.getOrDefault(entityType, "General");
    }

    // ---- Lightweight User-Agent parsing (no external dependency) ----

    private static final Pattern WINDOWS_VER = Pattern.compile("Windows NT ([0-9.]+)");
    private static final Map<String, String> WINDOWS_NAMES = Map.of(
        "10.0", "Windows 10/11", "6.3", "Windows 8.1", "6.2", "Windows 8",
        "6.1", "Windows 7", "6.0", "Windows Vista");

    private String parseOs(String ua) {
        if (ua == null) return null;
        if (ua.contains("Windows")) {
            Matcher m = WINDOWS_VER.matcher(ua);
            if (m.find()) return WINDOWS_NAMES.getOrDefault(m.group(1), "Windows");
            return "Windows";
        }
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) return "macOS";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iOS")) return "iOS";
        if (ua.contains("Linux")) return "Linux";
        return "Unknown";
    }

    private String parseBrowser(String ua) {
        if (ua == null) return null;
        if (ua.contains("Edg/"))                                  return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera"))          return "Opera";
        if (ua.contains("Chrome/") && !ua.contains("Chromium"))   return "Chrome";
        if (ua.contains("CriOS"))                                 return "Chrome (iOS)";
        if (ua.contains("Firefox/"))                              return "Firefox";
        if (ua.contains("Safari/") && !ua.contains("Chrome"))     return "Safari";
        return "Other";
    }

    // ---- Best-effort IP geolocation ----

    private String geolocate(String ip) {
        if (ip == null || ip.isBlank() || isPrivateOrLocal(ip)) return "Local/Private Network";
        String cached = GEO_CACHE.get(ip);
        if (cached != null) return cached;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = geoClient.getForObject(
                "http://ip-api.com/json/" + ip + "?fields=status,city,country", Map.class);
            if (resp == null || !"success".equals(resp.get("status"))) return null;
            String city = (String) resp.get("city");
            String country = (String) resp.get("country");
            String loc = (city != null && !city.isBlank()) ? city + ", " + country : country;
            if (loc != null) GEO_CACHE.put(ip, loc);
            return loc;
        } catch (Exception e) {
            return null; // geolocation is best-effort — never fail the audit entry over it
        }
    }

    private boolean isPrivateOrLocal(String ip) {
        return ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")
            || ip.startsWith("10.") || ip.startsWith("192.168.")
            || ip.startsWith("172.16.") || ip.startsWith("172.17.") || ip.startsWith("172.18.")
            || ip.startsWith("172.19.") || ip.startsWith("172.2") || ip.startsWith("172.3");
    }

    private RestTemplate buildGeoClient() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(2000);
        f.setReadTimeout(2000);
        return new RestTemplate(f);
    }
}
