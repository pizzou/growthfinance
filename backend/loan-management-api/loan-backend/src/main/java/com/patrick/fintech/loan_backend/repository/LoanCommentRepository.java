package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.LoanComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LoanCommentRepository extends JpaRepository<LoanComment, Long> {

    @Query("SELECT c FROM LoanComment c LEFT JOIN FETCH c.author WHERE c.loan.id = :loanId ORDER BY c.createdAt ASC")
    List<LoanComment> findByLoanIdOrderByCreatedAtAsc(@Param("loanId") Long loanId);

    @Query("SELECT c FROM LoanComment c LEFT JOIN FETCH c.author WHERE c.loan.id = :loanId AND c.visibleToApplicant = true ORDER BY c.createdAt ASC")
    List<LoanComment> findVisibleToApplicantByLoanId(@Param("loanId") Long loanId);
}
