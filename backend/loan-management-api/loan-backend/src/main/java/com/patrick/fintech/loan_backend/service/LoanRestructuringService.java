package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service @RequiredArgsConstructor @Slf4j
public class LoanRestructuringService {
    private final LoanRepository     loanRepo;
    private final PaymentRepository  paymentRepo;
    private final AuditService       auditService;
    private final WebhookService     webhookService;
    private final MailService        mailService;
    private final SmsService         smsService;

    @Transactional
    public Loan restructure(Long loanId,Long orgId,User officer,int newMonths,Double newRate,String reason) {
        Loan loan=get(loanId,orgId);
        if(loan.getStatus()!=LoanStatus.ACTIVE&&loan.getStatus()!=LoanStatus.OVERDUE&&loan.getStatus()!=LoanStatus.DEFAULTED)
            throw new RuntimeException("Only ACTIVE/OVERDUE/DEFAULTED loans can be restructured");
        double prevRate=loan.getInterestRate(); int prevMonths=loan.getDurationMonths();
        if(newRate!=null) loan.setInterestRate(newRate);
        loan.setDurationMonths(newMonths);
        loan.setStatus(LoanStatus.RESTRUCTURED);
        loan.setInternalNotes("[RESTRUCTURED] "+reason+" | "+prevMonths+"mo@"+prevRate+"% -> "+newMonths+"mo@"+loan.getInterestRate()+"%");
        regenerateSchedule(loan,officer);
        Loan saved=loanRepo.save(loan);
        audit(loan.getOrganization(),officer,"LOAN_RESTRUCTURED",loanId,"Restructured: "+reason);
        webhookService.dispatch(loan.getOrganization(),"LOAN_RESTRUCTURED",saved);
        notify(saved, () -> mailService.sendLoanRestructured(saved, reason),
            "Your loan " + saved.getReferenceNumber() + " has been restructured. New term: "
                + saved.getDurationMonths() + "mo at " + saved.getInterestRate() + "%.");
        return saved;
    }

    @Transactional
    public Loan writeOff(Long loanId,Long orgId,User officer,String reason) {
        Loan loan=get(loanId,orgId);
        if(loan.getStatus()==LoanStatus.PAID||loan.getStatus()==LoanStatus.CLOSED)
            throw new RuntimeException("Cannot write off a PAID or CLOSED loan");
        double amt=loan.getOutstandingBalance()!=null?loan.getOutstandingBalance():0;
        loan.setStatus(LoanStatus.WRITTEN_OFF);
        loan.setOutstandingBalance(0.0);
        loan.setInternalNotes("[WRITTEN OFF] "+reason+" | Amount: "+loan.getCurrency()+" "+amt+" | "+LocalDate.now());
        Loan saved=loanRepo.save(loan);
        audit(loan.getOrganization(),officer,"LOAN_WRITTEN_OFF",loanId,"Written off "+loan.getCurrency()+" "+amt+" | "+reason);
        webhookService.dispatch(loan.getOrganization(),"LOAN_WRITTEN_OFF",saved);
        notify(saved, () -> mailService.sendLoanWrittenOff(saved, reason),
            "There's an update on your loan " + saved.getReferenceNumber() + ". Please contact us for details.");
        return saved;
    }

    @Transactional
    public Loan grantMoratorium(Long loanId,Long orgId,User officer,int pauseMonths,String reason) {
        Loan loan=get(loanId,orgId);
        if(loan.getStatus()!=LoanStatus.ACTIVE&&loan.getStatus()!=LoanStatus.OVERDUE)
            throw new RuntimeException("Moratorium only applies to ACTIVE or OVERDUE loans");
        paymentRepo.findByLoanId(loanId).stream().filter(p->!p.getPaid()).forEach(p->{
            if(p.getDueDate()!=null) p.setDueDate(p.getDueDate().plusMonths(pauseMonths));
            paymentRepo.save(p);
        });
        if(loan.getMaturityDate()!=null) loan.setMaturityDate(loan.getMaturityDate().plusMonths(pauseMonths));
        if(loan.getNextDueDate()!=null)  loan.setNextDueDate(loan.getNextDueDate().plusMonths(pauseMonths));
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setInternalNotes((loan.getInternalNotes()!=null?loan.getInternalNotes()+" | ":"")+"[MORATORIUM "+pauseMonths+"mo] "+reason);
        Loan saved=loanRepo.save(loan);
        audit(loan.getOrganization(),officer,"MORATORIUM_GRANTED",loanId,pauseMonths+"mo moratorium: "+reason);
        notify(saved, () -> mailService.sendMoratoriumGranted(saved, pauseMonths, reason),
            "Your payments on loan " + saved.getReferenceNumber() + " are paused for " + pauseMonths
                + " month(s). Next due date: " + saved.getNextDueDate() + ".");
        return saved;
    }

    private void regenerateSchedule(Loan loan,User officer) {
        List<Payment> future=paymentRepo.findByLoanId(loan.getId()).stream().filter(p->!p.getPaid()).toList();
        paymentRepo.deleteAll(future);
        double bal=loan.getOutstandingBalance()!=null?loan.getOutstandingBalance():0;
        int months=loan.getDurationMonths(); double rate=loan.getInterestRate();
        String rateType=loan.getInterestRateType()!=null?loan.getInterestRateType():"MONTHLY";
        double mr="MONTHLY".equalsIgnoreCase(rateType)?rate/100:rate/100/12;
        double monthly=mr==0?bal/months:bal*(mr*Math.pow(1+mr,months))/(Math.pow(1+mr,months)-1);
        LocalDate due=LocalDate.now().plusMonths(1);
        for(int i=1;i<=months;i++){
            double interest=bal*mr,principalC=monthly-interest;
            bal=Math.max(0,bal-principalC);
            paymentRepo.save(Payment.builder()
                .paymentReference("PAY-"+loan.getReferenceNumber()+"-R"+String.format("%03d",i))
                .loan(loan).organization(loan.getOrganization()).recordedBy(officer)
                .installmentNumber(i).amount(r(monthly)).principalComponent(r(principalC))
                .interestComponent(r(interest)).dueDate(due).paid(false)
                .penalty(0.0).waivedAmount(0.0).outstandingAfter(r(bal))
                .status(Payment.PaymentStatus.PENDING).build());
            due=due.plusMonths(1);
        }
        loan.setNextDueDate(LocalDate.now().plusMonths(1));
    }

    private Loan get(Long loanId,Long orgId){
        Loan l=loanRepo.findById(loanId).orElseThrow(()->new RuntimeException("Loan not found: "+loanId));
        if(!l.getOrganization().getId().equals(orgId)) throw new RuntimeException("Access denied");
        return l;
    }
    private void audit(Organization org,User u,String action,Long id,String desc){
        auditService.log(org, u, action, "LOAN", id.toString(), desc);
    }

    /** Best-effort email + SMS, same pattern LoanService uses for approve/reject/disburse —
     *  a notification failure should never roll back the loan action that triggered it. */
    private void notify(Loan loan, Runnable sendEmail, String smsText) {
        if (loan.getBorrower() == null) return; // shouldn't happen; see LoanService's own guard
        try { sendEmail.run(); } catch (Exception e) { log.warn("Notif failed", e); }
        try {
            if (loan.getBorrower().getPhone() != null) smsService.sendCustom(loan.getBorrower().getPhone(), smsText);
        } catch (Exception e) { log.warn("SMS failed", e); }
    }

    private double r(double v){return Math.round(v*100.0)/100.0;}
}
