package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationRepository orgRepo;
    private final UserRepository         userRepo;
    private final CurrentUserUtil        currentUserUtil;
    private final com.patrick.fintech.loan_backend.service.AuditService auditService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String,Object>>> getMyOrg() {
        Organization org = orgRepo.findById(currentUserUtil.getCurrentUser().getOrganization().getId())
            .orElseThrow(() -> new RuntimeException("Organization not found"));

        Map<String,Object> m = new java.util.LinkedHashMap<>();
        m.put("id", org.getId()); m.put("name", org.getName());
        m.put("logoUrl", org.getLogoUrl()); m.put("primaryColor", org.getPrimaryColor());
        m.put("accentColor", org.getAccentColor()); m.put("website", org.getWebsite());
        m.put("contactEmail", org.getContactEmail()); m.put("contactPhone", org.getContactPhone());
        m.put("address", org.getAddress()); m.put("tagline", org.getTagline());
        m.put("mission", org.getMission()); m.put("vision", org.getVision());
        m.put("foundedYear", org.getFoundedYear()); m.put("mapUrl", org.getMapUrl());
        m.put("facebookUrl", org.getFacebookUrl()); m.put("instagramUrl", org.getInstagramUrl());
        m.put("linkedinUrl", org.getLinkedinUrl()); m.put("twitterUrl", org.getTwitterUrl());
        m.put("whatsappUrl", org.getWhatsappUrl());

        m.put("hero", Map.of(
            "headline", org.getHeroHeadline() != null ? org.getHeroHeadline() : "Your Trusted Financial Partner",
            "subtext",  org.getHeroSubtext()  != null ? org.getHeroSubtext()  : ""
        ));
        m.put("stats",        parseListOrEmpty(org.getStatsJson()));
        m.put("services",     parseListOrEmpty(org.getServicesJson()));
        m.put("testimonials", parseListOrEmpty(org.getTestimonialsJson()));
        m.put("team",         parseListOrEmpty(org.getTeamJson()));

        return ResponseEntity.ok(ApiResponse.ok(m));
    }

    private List<Map<String,Object>> parseListOrEmpty(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String,Object>> parsed = objectMapper.readValue(json,
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>>() {});
            return parsed != null ? parsed : List.of();
        } catch (Exception e) { return List.of(); }
    }

    /**
     * Lets an org admin update their own organization's public-facing website
     * content (branding, contact info, mission/vision, social links) as well
     * as core org settings. Restricted to ADMIN — this controls what every
     * visitor to the org's public site sees.
     */
    @PutMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Organization>> updateMyOrg(@RequestBody Map<String,Object> body) {
        Organization org = orgRepo.findById(currentUserUtil.getCurrentOrganizationId())
            .orElseThrow(() -> new RuntimeException("Organization not found"));

        // Core org settings
        setIfPresent(body, "name",            org::setName);
        setIfPresent(body, "contactEmail",    org::setContactEmail);
        setIfPresent(body, "contactPhone",    org::setContactPhone);
        setIfPresent(body, "address",         org::setAddress);
        setIfPresent(body, "defaultCurrency", org::setDefaultCurrency);
        setIfPresent(body, "timezone",        org::setTimezone);
        setIfPresent(body, "website",         org::setWebsite);
        setIfPresent(body, "logoUrl",         org::setLogoUrl);

        // Branding
        setIfPresent(body, "primaryColor", org::setPrimaryColor);
        setIfPresent(body, "accentColor",  org::setAccentColor);

        // Public website content
        setIfPresent(body, "tagline",       org::setTagline);
        setIfPresent(body, "mission",       org::setMission);
        setIfPresent(body, "vision",        org::setVision);
        setIfPresent(body, "mapUrl",        org::setMapUrl);
        setIfPresent(body, "facebookUrl",   org::setFacebookUrl);
        setIfPresent(body, "instagramUrl",  org::setInstagramUrl);
        setIfPresent(body, "linkedinUrl",   org::setLinkedinUrl);
        setIfPresent(body, "twitterUrl",    org::setTwitterUrl);
        setIfPresent(body, "whatsappUrl",   org::setWhatsappUrl);
        if (body.containsKey("foundedYear") && body.get("foundedYear") != null) {
            try { org.setFoundedYear(Integer.valueOf(body.get("foundedYear").toString())); }
            catch (NumberFormatException ignored) {}
        }

        // Home page hero
        if (body.containsKey("hero") && body.get("hero") instanceof Map<?,?> hero) {
            if (hero.get("headline") != null) org.setHeroHeadline(hero.get("headline").toString());
            if (hero.get("subtext")  != null) org.setHeroSubtext(hero.get("subtext").toString());
        }
        // Repeatable content lists — stored as JSON, editable in full from the dashboard
        setJsonIfPresent(body, "stats",        org::setStatsJson);
        setJsonIfPresent(body, "services",     org::setServicesJson);
        setJsonIfPresent(body, "testimonials", org::setTestimonialsJson);
        setJsonIfPresent(body, "team",         org::setTeamJson);

        org = orgRepo.save(org);
        auditService.log(org, currentUserUtil.getCurrentUser(), "ORGANIZATION_UPDATED", "ORGANIZATION",
            org.getId().toString(), "Website/organization settings updated");
        return ResponseEntity.ok(ApiResponse.ok("Updated", org));
    }

    private void setIfPresent(Map<String,Object> body, String key, java.util.function.Consumer<String> setter) {
        if (body.containsKey(key) && body.get(key) != null) setter.accept(body.get(key).toString());
    }

    private void setJsonIfPresent(Map<String,Object> body, String key, java.util.function.Consumer<String> setter) {
        if (!body.containsKey(key) || body.get(key) == null) return;
        try { setter.accept(objectMapper.writeValueAsString(body.get(key))); }
        catch (Exception e) { throw new RuntimeException("Invalid " + key + " content: " + e.getMessage()); }
    }

    @GetMapping("/me/users")
    public ResponseEntity<ApiResponse<List<User>>> getUsers() {
        Organization org = currentUserUtil.getCurrentUser().getOrganization();
        return ResponseEntity.ok(ApiResponse.ok(userRepo.findByOrganization(org)));
    }
}
