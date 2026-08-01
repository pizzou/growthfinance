package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportingService {

    private final LoanRepository loanRepository;
    private final PaymentRepository paymentRepository;

    public ReportingService(LoanRepository loanRepository, PaymentRepository paymentRepository) {
        this.loanRepository = loanRepository;
        this.paymentRepository = paymentRepository;
    }

    public Map<String, Long> loanStatusReport(Long organizationId) {
        List<Loan> loans = loanRepository.findByOrganization_Id(organizationId);
        return loans.stream().collect(Collectors.groupingBy(
            loan -> loan.getStatus().name(), Collectors.counting()));
    }

    public Map<String, Double> paymentReport(Long organizationId) {
        List<Payment> payments = paymentRepository.findByLoan_Organization_Id(organizationId);

        double totalPaid = payments.stream()
            .filter(p -> Boolean.TRUE.equals(p.getPaid()))
            .mapToDouble(p -> p.getAmount() != null ? p.getAmount() : 0.0).sum();

        double totalPending = payments.stream()
            .filter(p -> !Boolean.TRUE.equals(p.getPaid()))
            .mapToDouble(p -> p.getAmount() != null ? p.getAmount() : 0.0).sum();

        double totalPenalties = payments.stream()
            .mapToDouble(p -> p.getPenalty() != null ? p.getPenalty() : 0.0).sum();

        return Map.of("totalPaid", totalPaid, "totalPending", totalPending, "totalPenalties", totalPenalties);
    }

    // ---- CSV exports ----

    private String csvField(Object v) {
        if (v == null) return "";
        String s = v.toString();
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    public String exportLoansCsv(Long organizationId) {
        List<Loan> loans = loanRepository.findByOrganization_Id(organizationId);
        StringBuilder csv = new StringBuilder(
            "Reference,Borrower,Status,Amount,Currency,InterestRate,DurationMonths,OutstandingBalance,LoanOfficer,Branch,CreatedAt\n");
        for (Loan l : loans) {
            csv.append(csvField(l.getReferenceNumber())).append(',')
               .append(csvField(l.getBorrower() != null ? l.getBorrower().getFirstName() + " " + l.getBorrower().getLastName() : "")).append(',')
               .append(csvField(l.getStatus())).append(',')
               .append(csvField(l.getAmount())).append(',')
               .append(csvField(l.getCurrency())).append(',')
               .append(csvField(l.getInterestRate())).append(',')
               .append(csvField(l.getDurationMonths())).append(',')
               .append(csvField(l.getOutstandingBalance())).append(',')
               .append(csvField(l.getLoanOfficer() != null ? l.getLoanOfficer().getName() : "")).append(',')
               .append(csvField(l.getBranch() != null ? l.getBranch().getName() : "")).append(',')
               .append(csvField(l.getCreatedAt())).append('\n');
        }
        return csv.toString();
    }

    public String exportPaymentsCsv(Long organizationId) {
        List<Payment> payments = paymentRepository.findByLoan_Organization_Id(organizationId);
        StringBuilder csv = new StringBuilder("LoanReference,DueDate,Amount,Penalty,Paid,PaidDate,PaymentReference\n");
        for (Payment p : payments) {
            csv.append(csvField(p.getLoan() != null ? p.getLoan().getReferenceNumber() : "")).append(',')
               .append(csvField(p.getDueDate())).append(',')
               .append(csvField(p.getAmount())).append(',')
               .append(csvField(p.getPenalty())).append(',')
               .append(csvField(p.getPaid())).append(',')
               .append(csvField(p.getPaidDate())).append(',')
               .append(csvField(p.getPaymentReference())).append('\n');
        }
        return csv.toString();
    }

    public String exportOverdueCsv(Long organizationId) {
        java.time.LocalDate today = java.time.LocalDate.now();
        List<Payment> overdue = paymentRepository.findByLoan_Organization_Id(organizationId).stream()
            .filter(p -> !Boolean.TRUE.equals(p.getPaid()) && p.getDueDate() != null && p.getDueDate().isBefore(today))
            .toList();
        StringBuilder csv = new StringBuilder("LoanReference,Borrower,DueDate,DaysOverdue,Amount,Penalty\n");
        for (Payment p : overdue) {
            long daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(p.getDueDate(), today);
            Loan l = p.getLoan();
            csv.append(csvField(l != null ? l.getReferenceNumber() : "")).append(',')
               .append(csvField(l != null && l.getBorrower() != null ? l.getBorrower().getFirstName() + " " + l.getBorrower().getLastName() : "")).append(',')
               .append(csvField(p.getDueDate())).append(',')
               .append(csvField(daysOverdue)).append(',')
               .append(csvField(p.getAmount())).append(',')
               .append(csvField(p.getPenalty())).append('\n');
        }
        return csv.toString();
    }

    public String exportPortfolioSummaryCsv(Long organizationId) {
        Map<String, Long> statusCounts = loanStatusReport(organizationId);
        Map<String, Double> payments = paymentReport(organizationId);
        StringBuilder csv = new StringBuilder("Metric,Value\n");
        statusCounts.forEach((status, count) -> csv.append("Loans - ").append(csvField(status)).append(',').append(count).append('\n'));
        payments.forEach((k, v) -> csv.append(csvField(k)).append(',').append(v).append('\n'));
        return csv.toString();
    }
}