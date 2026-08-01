
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportingService {

    private final LoanRepository loanRepository;
    private final PaymentRepository paymentRepository;

    public ReportingService(
            LoanRepository loanRepository,
            PaymentRepository paymentRepository) {

        this.loanRepository = loanRepository;
        this.paymentRepository = paymentRepository;
    }

    // ============================================================
    // LOAN STATUS REPORT
    // ============================================================

    public Map<String, Long> loanStatusReport(Long organizationId) {

        List<Loan> loans =
                loanRepository.findByOrganization_Id(organizationId);

        return loans.stream()
                .filter(loan -> loan.getStatus() != null)
                .collect(Collectors.groupingBy(
                        loan -> loan.getStatus().name(),
                        Collectors.counting()
                ));
    }

    // ============================================================
    // PAYMENT REPORT
    // ============================================================

    public Map<String, BigDecimal> paymentReport(Long organizationId) {

        List<Payment> payments =
                paymentRepository.findByLoan_Organization_Id(organizationId);

        BigDecimal totalPaid =
                payments.stream()
                        .filter(p -> Boolean.TRUE.equals(p.getPaid()))
                        .map(Payment::getAmount)
                        .filter(amount -> amount != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPending =
                payments.stream()
                        .filter(p -> !Boolean.TRUE.equals(p.getPaid()))
                        .map(Payment::getAmount)
                        .filter(amount -> amount != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPenalties =
                payments.stream()
                        .map(Payment::getPenalty)
                        .filter(penalty -> penalty != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
                "totalPaid", totalPaid,
                "totalPending", totalPending,
                "totalPenalties", totalPenalties
        );
    }

    // ============================================================
    // CSV HELPER
    // ============================================================

    private String csvField(Object value) {

        if (value == null) {
            return "";
        }

        String s = value.toString();

        if (s.contains(",")
                || s.contains("\"")
                || s.contains("\n")
                || s.contains("\r")) {

            return "\"" + s.replace("\"", "\"\"") + "\"";
        }

        return s;
    }

    // ============================================================
    // LOANS CSV
    // ============================================================

    public String exportLoansCsv(Long organizationId) {

        List<Loan> loans =
                loanRepository.findByOrganization_Id(organizationId);

        StringBuilder csv = new StringBuilder(
                "Reference,Borrower,Status,Amount,Currency,"
                        + "InterestRate,DurationMonths,OutstandingBalance,"
                        + "LoanOfficer,Branch,CreatedAt\n"
        );

        for (Loan l : loans) {

            String borrowerName = "";

            if (l.getBorrower() != null) {

                String firstName = l.getBorrower().getFirstName();
                String lastName = l.getBorrower().getLastName();

                borrowerName =
                        ((firstName != null ? firstName : "")
                                + " "
                                + (lastName != null ? lastName : ""))
                                .trim();
            }

            csv.append(csvField(l.getReferenceNumber()))
                    .append(',')

                    .append(csvField(borrowerName))
                    .append(',')

                    .append(csvField(l.getStatus()))
                    .append(',')

                    .append(csvField(l.getAmount()))
                    .append(',')

                    .append(csvField(l.getCurrency()))
                    .append(',')

                    .append(csvField(l.getInterestRate()))
                    .append(',')

                    .append(csvField(l.getDurationMonths()))
                    .append(',')

                    .append(csvField(l.getOutstandingBalance()))
                    .append(',')

                    .append(csvField(
                            l.getLoanOfficer() != null
                                    ? l.getLoanOfficer().getName()
                                    : ""
                    ))
                    .append(',')

                    .append(csvField(
                            l.getBranch() != null
                                    ? l.getBranch().getName()
                                    : ""
                    ))
                    .append(',')

                    .append(csvField(l.getCreatedAt()))
                    .append('\n');
        }

        return csv.toString();
    }

    // ============================================================
    // PAYMENTS CSV
    // ============================================================

    public String exportPaymentsCsv(Long organizationId) {

        List<Payment> payments =
                paymentRepository.findByLoan_Organization_Id(organizationId);

        StringBuilder csv = new StringBuilder(
                "LoanReference,DueDate,Amount,Penalty,"
                        + "Paid,PaidDate,PaymentReference\n"
        );

        for (Payment p : payments) {

            csv.append(csvField(
                            p.getLoan() != null
                                    ? p.getLoan().getReferenceNumber()
                                    : ""
                    ))
                    .append(',')

                    .append(csvField(p.getDueDate()))
                    .append(',')

                    .append(csvField(p.getAmount()))
                    .append(',')

                    .append(csvField(p.getPenalty()))
                    .append(',')

                    .append(csvField(p.getPaid()))
                    .append(',')

                    .append(csvField(p.getPaidDate()))
                    .append(',')

                    .append(csvField(p.getPaymentReference()))
                    .append('\n');
        }

        return csv.toString();
    }

    // ============================================================
    // OVERDUE CSV
    // ============================================================

    public String exportOverdueCsv(Long organizationId) {

        java.time.LocalDate today =
                java.time.LocalDate.now();

        List<Payment> overdue =
                paymentRepository
                        .findByLoan_Organization_Id(organizationId)
                        .stream()
                        .filter(p ->
                                !Boolean.TRUE.equals(p.getPaid())
                                        && p.getDueDate() != null
                                        && p.getDueDate().isBefore(today)
                        )
                        .toList();

        StringBuilder csv =
                new StringBuilder(
                        "LoanReference,Borrower,DueDate,"
                                + "DaysOverdue,Amount,Penalty\n"
                );

        for (Payment p : overdue) {

            long daysOverdue =
                    java.time.temporal.ChronoUnit.DAYS.between(
                            p.getDueDate(),
                            today
                    );

            Loan loan = p.getLoan();

            String borrowerName = "";

            if (loan != null && loan.getBorrower() != null) {

                String firstName =
                        loan.getBorrower().getFirstName();

                String lastName =
                        loan.getBorrower().getLastName();

                borrowerName =
                        ((firstName != null ? firstName : "")
                                + " "
                                + (lastName != null ? lastName : ""))
                                .trim();
            }

            csv.append(csvField(
                            loan != null
                                    ? loan.getReferenceNumber()
                                    : ""
                    ))
                    .append(',')

                    .append(csvField(borrowerName))
                    .append(',')

                    .append(csvField(p.getDueDate()))
                    .append(',')

                    .append(csvField(daysOverdue))
                    .append(',')

                    .append(csvField(p.getAmount()))
                    .append(',')

                    .append(csvField(p.getPenalty()))
                    .append('\n');
        }

        return csv.toString();
    }

    // ============================================================
    // PORTFOLIO SUMMARY CSV
    // ============================================================

    public String exportPortfolioSummaryCsv(Long organizationId) {

        Map<String, Long> statusCounts =
                loanStatusReport(organizationId);

        Map<String, BigDecimal> payments =
                paymentReport(organizationId);

        StringBuilder csv =
                new StringBuilder("Metric,Value\n");

        statusCounts.forEach((status, count) ->
                csv.append(csvField("Loans - " + status))
                        .append(',')
                        .append(count)
                        .append('\n')
        );

        payments.forEach((key, value) ->
                csv.append(csvField(key))
                        .append(',')
                        .append(csvField(value))
                        .append('\n')
        );

        return csv.toString();
    }
}
