package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class BorrowerService {

    private final BorrowerRepository     repo;
    private final OrganizationRepository orgRepo;

    public BorrowerService(BorrowerRepository repo, OrganizationRepository orgRepo) {
        this.repo    = repo;
        this.orgRepo = orgRepo;
    }

    public Borrower create(Borrower borrower, Long orgId) {
        Organization org = orgRepo.findById(orgId)
            .orElseThrow(() -> new RuntimeException("Organization not found: " + orgId));

        if (borrower.getNationalId() != null && !borrower.getNationalId().isBlank()) {
            repo.findByNationalIdHashAndOrganization_Id(
                    com.patrick.fintech.loan_backend.security.HmacIndexer.index(borrower.getNationalId()), orgId)
                .ifPresent(b -> { throw new RuntimeException(
                    "A borrower with National ID \"" + borrower.getNationalId() +
                    "\" already exists: " + b.getFirstName() + " " + b.getLastName()); });
        }
        if (borrower.getPhone() != null && !borrower.getPhone().isBlank()) {
            repo.findByPhoneHashAndOrganization_Id(
                    com.patrick.fintech.loan_backend.security.HmacIndexer.index(borrower.getPhone()), orgId)
                .ifPresent(b -> { throw new RuntimeException(
                    "A borrower with phone \"" + borrower.getPhone() +
                    "\" already exists: " + b.getFirstName() + " " + b.getLastName()); });
        }
        if (borrower.getEmail() != null && !borrower.getEmail().isBlank()) {
            repo.findByEmailAndOrganization_Id(borrower.getEmail(), orgId)
                .ifPresent(b -> { throw new RuntimeException(
                    "A borrower with email \"" + borrower.getEmail() +
                    "\" already exists: " + b.getFirstName() + " " + b.getLastName()); });
        }

        borrower.setOrganization(org);
        if (borrower.getKycStatus() == null) borrower.setKycStatus("PENDING");
        return repo.save(borrower);
    }

    public Borrower update(Long id, Borrower updated) {
        Borrower b     = getById(id);
        Long     orgId = b.getOrganization().getId();

        if (updated.getNationalId() != null && !updated.getNationalId().isBlank()
                && !updated.getNationalId().equals(b.getNationalId())) {
            repo.findByNationalIdHashAndOrganization_Id(
                    com.patrick.fintech.loan_backend.security.HmacIndexer.index(updated.getNationalId()), orgId)
                .ifPresent(e -> { throw new RuntimeException(
                    "National ID \"" + updated.getNationalId() +
                    "\" is already used by: " + e.getFirstName() + " " + e.getLastName()); });
        }
        if (updated.getPhone() != null && !updated.getPhone().isBlank()
                && !updated.getPhone().equals(b.getPhone())) {
            repo.findByPhoneHashAndOrganization_Id(
                    com.patrick.fintech.loan_backend.security.HmacIndexer.index(updated.getPhone()), orgId)
                .ifPresent(e -> { throw new RuntimeException(
                    "Phone \"" + updated.getPhone() +
                    "\" is already used by: " + e.getFirstName() + " " + e.getLastName()); });
        }
        if (updated.getEmail() != null && !updated.getEmail().isBlank()
                && !updated.getEmail().equals(b.getEmail())) {
            repo.findByEmailAndOrganization_Id(updated.getEmail(), orgId)
                .ifPresent(e -> { throw new RuntimeException(
                    "Email \"" + updated.getEmail() +
                    "\" is already used by: " + e.getFirstName() + " " + e.getLastName()); });
        }

        if (updated.getFirstName()   != null) b.setFirstName(updated.getFirstName());
        if (updated.getLastName()    != null) b.setLastName(updated.getLastName());
        if (updated.getPhone()       != null) b.setPhone(updated.getPhone());
        if (updated.getEmail()       != null) b.setEmail(updated.getEmail());
        if (updated.getAddress()     != null) b.setAddress(updated.getAddress());
        if (updated.getNationalId()  != null) b.setNationalId(updated.getNationalId());
        if (updated.getCreditScore() != null) b.setCreditScore(updated.getCreditScore());
        if (updated.getKycStatus()   != null) b.setKycStatus(updated.getKycStatus());
        return repo.save(b);
    }

    public List<Borrower> listByOrg(Long orgId) {
        return repo.findByOrganization_Id(orgId);
    }

    public List<Borrower> search(Long orgId, String query) {
        if (query == null || query.isBlank()) return listByOrg(orgId);
        String q = query.toLowerCase();
        return listByOrg(orgId).stream()
            .filter(b -> contains(b.getFirstName(), q) || contains(b.getLastName(), q)
                      || contains(b.getNationalId(), q) || contains(b.getPhone(), q))
            .toList();
    }

    public Borrower getById(Long id) {
        return repo.findById(id)
            .orElseThrow(() -> new RuntimeException("Borrower not found: " + id));
    }

    public Borrower getByIdForOrg(Long id, Long orgId) {
        Borrower b = getById(id);
        if (!b.getOrganization().getId().equals(orgId))
            throw new RuntimeException("Borrower not found: " + id);
        return b;
    }

    private boolean contains(String field, String q) {
        return field != null && field.toLowerCase().contains(q);
    }
}