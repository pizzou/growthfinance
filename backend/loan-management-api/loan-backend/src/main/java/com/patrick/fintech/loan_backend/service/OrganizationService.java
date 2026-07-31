package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class OrganizationService {

    private final OrganizationRepository repo;

    public OrganizationService(OrganizationRepository repo) { this.repo = repo; }

    public Organization create(Organization org) { return repo.save(org); }
    public Organization getById(Long id) {
        return repo.findById(id).orElseThrow(() -> new RuntimeException("Organization not found: " + id));
    }
    public List<Organization> getAll() { return repo.findAll(); }
    public Organization update(Long id, Organization updated) {
        Organization org = getById(id);
        org.setName(updated.getName());
        if (updated.getIndustry() != null) org.setIndustry(updated.getIndustry());
        return repo.save(org);
    }
    public void delete(Long id) { repo.deleteById(id); }
}