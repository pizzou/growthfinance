
package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.ChartOfAccount;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;
import java.util.Optional;


public interface ChartOfAccountRepository
        extends JpaRepository<ChartOfAccount, Long> {

    /*
     * ============================================================
     * ALL ACCOUNTS FOR ORGANIZATION
     * ============================================================
     */

    List<ChartOfAccount> findByOrganization_IdOrderByCodeAsc(
            Long organizationId
    );


    /*
     * ============================================================
     * ACTIVE ACCOUNTS
     * ============================================================
     */

    List<ChartOfAccount>
    findByOrganization_IdAndActiveTrueOrderByCodeAsc(
            Long organizationId
    );


    /*
     * ============================================================
     * ACCOUNT BY CODE
     * ============================================================
     */

    Optional<ChartOfAccount> findByOrganization_IdAndCode(
            Long organizationId,
            String code
    );


    /*
     * ============================================================
     * ACTIVE ACCOUNT BY CODE
     * ============================================================
     *
     * Useful when posting accounting transactions.
     *
     * An inactive account should normally not be used for a new
     * journal entry.
     */

    Optional<ChartOfAccount>
    findByOrganization_IdAndCodeAndActiveTrue(
            Long organizationId,
            String code
    );


    /*
     * ============================================================
     * ACCOUNT BY ID + ORGANIZATION
     * ============================================================
     *
     * Prevents accidentally retrieving an account belonging to
     * another organization.
     */

    Optional<ChartOfAccount> findByIdAndOrganization_Id(
            Long id,
            Long organizationId
    );


    /*
     * ============================================================
     * ACCOUNT EXISTENCE BY ORGANIZATION + CODE
     * ============================================================
     */

    boolean existsByOrganization_IdAndCode(
            Long organizationId,
            String code
    );


    /*
     * ============================================================
     * ACCOUNT EXISTENCE BY ORGANIZATION + ID
     * ============================================================
     */

    boolean existsByIdAndOrganization_Id(
            Long id,
            Long organizationId
    );
}
