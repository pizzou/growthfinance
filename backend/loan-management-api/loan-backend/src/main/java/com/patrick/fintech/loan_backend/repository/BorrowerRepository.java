package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BorrowerRepository extends JpaRepository<Borrower, Long> {
    Optional<Borrower> findByEmailAndOrganization(String email, Organization organization);
    boolean existsByEmailAndOrganization(String email, Organization organization);
    long countByOrganization(Organization organization);
    Page<Borrower> findByOrganization(Organization organization, Pageable pageable);

    @Query("SELECT b FROM Borrower b WHERE b.organization = :org " +
           "AND (LOWER(b.firstName) LIKE LOWER(CONCAT('%',:q,'%')) " +
           "OR LOWER(b.lastName) LIKE LOWER(CONCAT('%',:q,'%')) " +
           "OR LOWER(b.email) LIKE LOWER(CONCAT('%',:q,'%')))")
    // Note: national ID and phone are encrypted at rest (see CryptoConverter) and can no longer be
    // substring-searched — only exact-match via their HMAC blind index (nationalIdHash / phoneHash).
    // This is an intentional, standard trade-off of field-level encryption. If a national-ID search box
    // is needed, search by the full number and it'll match through findByNationalIdHashAndOrganization_Id.
    Page<Borrower> search(@Param("org") Organization org, @Param("q") String query, Pageable pageable);

    List<Borrower> findByOrganization_Id(Long orgId);

    /** National ID and phone are encrypted — query by their deterministic hash (see HmacIndexer), not the raw value. */
    Optional<Borrower> findByNationalIdHashAndOrganization_Id(String nationalIdHash, Long orgId);
    Optional<Borrower> findByPhoneHashAndOrganization_Id(String phoneHash, Long orgId);
    Optional<Borrower> findByEmailAndOrganization_Id(String email, Long orgId);
}
