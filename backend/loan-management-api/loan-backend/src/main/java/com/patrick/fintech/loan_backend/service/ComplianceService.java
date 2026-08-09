package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class ComplianceService {
    private final KycCheckRepository kycRepo;
    private final BorrowerRepository borrowerRepo;
    private final AuditService auditService;

    private static final Set<String> SAMPLE_WATCHLIST = Set.of("JOHN DOE SANCTIONED","JANE SMITH PEP TEST");

    @Transactional
    public KycCheck runFullScreening(Long borrowerId, Long orgId) {
        Borrower b = borrowerRepo.findById(borrowerId)
            .orElseThrow(()->new RuntimeException("Borrower not found: "+borrowerId));
        KycCheck.CheckResult sanctions = screenWatchlists(b);
        KycCheck.CheckResult identity  = verifyDocs(b);
        KycCheck.CheckResult overall   = (sanctions==KycCheck.CheckResult.FLAGGED||identity==KycCheck.CheckResult.REJECTED)
            ? KycCheck.CheckResult.MANUAL_REVIEW : KycCheck.CheckResult.CLEAR;
        KycCheck check = kycRepo.save(KycCheck.builder()
            .borrower(b).organization(b.getOrganization())
            .checkType(KycCheck.CheckType.SANCTIONS_SCREENING).result(overall)
            .matchScore(overall==KycCheck.CheckResult.CLEAR?0.0:65.0)
            .provider("INTERNAL").notes("Sanctions:"+sanctions+" | Identity:"+identity).build());
        b.setKycStatus(overall==KycCheck.CheckResult.CLEAR?"VERIFIED":overall==KycCheck.CheckResult.MANUAL_REVIEW?"PENDING_REVIEW":"REJECTED");
        borrowerRepo.save(b);
        log.info("KYC screening for borrower {}: {}",borrowerId,overall);
        return check;
    }

    @Transactional
    public KycCheck manualReview(Long checkId,String reviewer,KycCheck.CheckResult decision,String notes) {
        KycCheck c = kycRepo.findById(checkId).orElseThrow(()->new RuntimeException("KYC check not found"));
        c.setResult(decision); c.setReviewedBy(reviewer);
        c.setReviewedAt(LocalDateTime.now());
        c.setNotes((c.getNotes()!=null?c.getNotes()+" | ":"")+"Manual: "+notes);
        Borrower b = c.getBorrower();
        b.setKycStatus(decision==KycCheck.CheckResult.CLEAR?"VERIFIED":"REJECTED");
        borrowerRepo.save(b);
        auditService.log(c.getOrganization(), null,
            "KYC_MANUAL_REVIEW", "KYC_CHECK", checkId.toString(), "Decision: "+decision+" by "+reviewer);
        return kycRepo.save(c);
    }

    public List<KycCheck> getPendingReviews(Long orgId) {
        return kycRepo.findByOrganization_IdAndResult(orgId,KycCheck.CheckResult.MANUAL_REVIEW);
    }
    public List<KycCheck> getHistoryForBorrower(Long borrowerId) { return kycRepo.findByBorrower_Id(borrowerId); }
    public boolean isKycCurrentlyClear(Long borrowerId) {
        return kycRepo.findFirstByBorrower_IdAndCheckTypeOrderByCreatedAtDesc(borrowerId,KycCheck.CheckType.SANCTIONS_SCREENING)
            .filter(c->c.getResult()==KycCheck.CheckResult.CLEAR&&!c.isExpired()).isPresent();
    }

    private KycCheck.CheckResult screenWatchlists(Borrower b) {
        String name=(b.getFirstName()+" "+b.getLastName()).trim().toUpperCase();
        return SAMPLE_WATCHLIST.stream().anyMatch(w->fuzzy(name,w))?KycCheck.CheckResult.FLAGGED:KycCheck.CheckResult.CLEAR;
    }
    private KycCheck.CheckResult verifyDocs(Borrower b) {
        return (b.getNationalId()!=null&&!b.getNationalId().isBlank())||(b.getPassportNumber()!=null&&!b.getPassportNumber().isBlank())
            ?KycCheck.CheckResult.CLEAR:KycCheck.CheckResult.REJECTED;
    }
    private boolean fuzzy(String a,String w) {
        if(a.equals(w)) return true;
        int d=lev(a,w),m=Math.max(a.length(),w.length());
        return m>0&&((double)d/m)<0.15;
    }
    private int lev(String a,String b) {
        int[][]dp=new int[a.length()+1][b.length()+1];
        for(int i=0;i<=a.length();i++)dp[i][0]=i;
        for(int j=0;j<=b.length();j++)dp[0][j]=j;
        for(int i=1;i<=a.length();i++) for(int j=1;j<=b.length();j++) {
            int c=a.charAt(i-1)==b.charAt(j-1)?0:1;
            dp[i][j]=Math.min(Math.min(dp[i-1][j]+1,dp[i][j-1]+1),dp[i-1][j-1]+c);
        }
        return dp[a.length()][b.length()];
    }
}
