package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportingService {


private final LoanRepository loanRepository;
private final PaymentRepository paymentRepository;

public ReportingService(
        LoanRepository loanRepository,
        PaymentRepository paymentRepository
) {
    this.loanRepository = loanRepository;
    this.paymentRepository = paymentRepository;
}

// ================================================================
// LOAN STATUS REPORT
// ================================================================

public Map<String, Long> loanStatusReport(Long organizationId) {

    List<Loan> loans =
            loanRepository.findByOrganization_Id(
                    organizationId
            );

    return loans.stream()
            .collect(
                    Collectors.groupingBy(
                            loan -> loan.getStatus().name(),
                            Collectors.counting()
                    )
            );
}

// ================================================================
// PAYMENT REPORT
// ================================================================

public Map<String, Double> paymentReport(
        Long organizationId
) {

    List<Payment> payments =
            paymentRepository.findByLoan_Organization_Id(
                    organizationId
            );

    double totalPaid =
            payments.stream()
                    .filter(
                            p -> Boolean.TRUE.equals(
                                    p.getPaid()
                            )
                    )
                    .mapToDouble(
                            p -> p.getAmount() != null
                                    ? p.getAmount()
                                    : 0.0
                    )
                    .sum();

    double totalPending =
            payments.stream()
                    .filter(
                            p -> !Boolean.TRUE.equals(
                                    p.getPaid()
                            )
                    )
                    .mapToDouble(
                            p -> p.getAmount() != null
                                    ? p.getAmount()
                                    : 0.0
                    )
                    .sum();

    double totalPenalties =
            payments.stream()
                    .mapToDouble(
                            p -> p.getPenalty() != null
                                    ? p.getPenalty()
                                    : 0.0
                    )
                    .sum();

    return Map.of(
            "totalPaid",
            totalPaid,

            "totalPending",
            totalPending,

            "totalPenalties",
            totalPenalties
    );
}

// ================================================================
// CSV HELPER
// ================================================================

private String csvField(Object value) {

    if (value == null) {
        return "";
    }

    String s =
            value.toString();

    if (
            s.contains(",")
                    || s.contains("\"")
                    || s.contains("\n")
    ) {

        return "\""
                + s.replace(
                        "\"",
                        "\"\""
                )
                + "\"";
    }

    return s;
}

// ================================================================
// LOANS CSV
// ================================================================

public String exportLoansCsv(
        Long organizationId
) {

    List<Loan> loans =
            loanRepository.findByOrganization_Id(
                    organizationId
            );

    StringBuilder csv =
            new StringBuilder(
                    "Reference,Borrower,Status,Amount,Currency,InterestRate,DurationMonths,OutstandingBalance,LoanOfficer,Branch,CreatedAt\n"
            );

    for (Loan l : loans) {

        csv.append(
                csvField(
                        l.getReferenceNumber()
                )
        ).append(',')

        .append(
                csvField(
                        l.getBorrower() != null
                                ? l.getBorrower().getFirstName()
                                + " "
                                + l.getBorrower().getLastName()
                                : ""
                )
        ).append(',')

        .append(
                csvField(
                        l.getStatus()
                )
        ).append(',')

        .append(
                csvField(
                        l.getAmount()
                )
        ).append(',')

        .append(
                csvField(
                        l.getCurrency()
                )
        ).append(',')

        .append(
                csvField(
                        l.getInterestRate()
                )
        ).append(',')

        .append(
                csvField(
                        l.getDurationMonths()
                )
        ).append(',')

        .append(
                csvField(
                        l.getOutstandingBalance()
                )
        ).append(',')

        .append(
                csvField(
                        l.getLoanOfficer() != null
                                ? l.getLoanOfficer().getName()
                                : ""
                )
        ).append(',')

        .append(
                csvField(
                        l.getBranch() != null
                                ? l.getBranch().getName()
                                : ""
                )
        ).append(',')

        .append(
                csvField(
                        l.getCreatedAt()
                )
        ).append('\n');
    }

    return csv.toString();
}

// ================================================================
// PAYMENTS CSV
// ================================================================

public String exportPaymentsCsv(
        Long organizationId
) {

    List<Payment> payments =
            paymentRepository.findByLoan_Organization_Id(
                    organizationId
            );

    StringBuilder csv =
            new StringBuilder(
                    "LoanReference,DueDate,Amount,Penalty,Paid,PaidDate,PaymentReference\n"
            );

    for (Payment p : payments) {

        csv.append(
                csvField(
                        p.getLoan() != null
                                ? p.getLoan().getReferenceNumber()
                                : ""
                )
        ).append(',')

        .append(
                csvField(
                        p.getDueDate()
                )
        ).append(',')

        .append(
                csvField(
                        p.getAmount()
                )
        ).append(',')

        .append(
                csvField(
                        p.getPenalty()
                )
        ).append(',')

        .append(
                csvField(
                        p.getPaid()
                )
        ).append(',')

        .append(
                csvField(
                        p.getPaidDate()
                )
        ).append(',')

        .append(
                csvField(
                        p.getPaymentReference()
                )
        ).append('\n');
    }

    return csv.toString();
}

// ================================================================
// OVERDUE CSV
// ================================================================

public String exportOverdueCsv(
        Long organizationId
) {

    LocalDate today =
            LocalDate.now();

    List<Payment> overdue =
            paymentRepository
                    .findByLoan_Organization_Id(
                            organizationId
                    )
                    .stream()
                    .filter(
                            p ->
                                    !Boolean.TRUE.equals(
                                            p.getPaid()
                                    )
                                            && p.getDueDate() != null
                                            && p.getDueDate()
                                            .isBefore(today)
                    )
                    .toList();

    StringBuilder csv =
            new StringBuilder(
                    "LoanReference,Borrower,DueDate,DaysOverdue,Amount,Penalty\n"
            );

    for (Payment p : overdue) {

        long daysOverdue =
                ChronoUnit.DAYS.between(
                        p.getDueDate(),
                        today
                );

        Loan l =
                p.getLoan();

        csv.append(
                csvField(
                        l != null
                                ? l.getReferenceNumber()
                                : ""
                )
        ).append(',')

        .append(
                csvField(
                        l != null
                                && l.getBorrower() != null
                                ? l.getBorrower().getFirstName()
                                + " "
                                + l.getBorrower().getLastName()
                                : ""
                )
        ).append(',')

        .append(
                csvField(
                        p.getDueDate()
                )
        ).append(',')

        .append(
                csvField(
                        daysOverdue
                )
        ).append(',')

        .append(
                csvField(
                        p.getAmount()
                )
        ).append(',')

        .append(
                csvField(
                        p.getPenalty()
                )
        ).append('\n');
    }

    return csv.toString();
}

// ================================================================
// PORTFOLIO SUMMARY CSV
// ================================================================

public String exportPortfolioSummaryCsv(
        Long organizationId
) {

    Map<String, Long> statusCounts =
            loanStatusReport(
                    organizationId
            );

    Map<String, Double> payments =
            paymentReport(
                    organizationId
            );

    StringBuilder csv =
            new StringBuilder(
                    "Metric,Value\n"
            );

    statusCounts.forEach(
            (status, count) ->
                    csv.append(
                                    "Loans - "
                            )
                            .append(
                                    csvField(
                                            status
                                    )
                            )
                            .append(',')
                            .append(
                                    count
                            )
                            .append('\n')
    );

    payments.forEach(
            (key, value) ->
                    csv.append(
                                    csvField(
                                            key
                                    )
                            )
                            .append(',')
                            .append(
                                    value
                            )
                            .append('\n')
    );

    return csv.toString();
}

// ================================================================
// EXCEL STYLE
// ================================================================

private CellStyle createHeaderStyle(
        Workbook workbook
) {

    CellStyle style =
            workbook.createCellStyle();

    Font font =
            workbook.createFont();

    font.setBold(
            true
    );

    style.setFont(
            font
    );

    style.setBorderBottom(
            BorderStyle.THIN
    );

    style.setBorderTop(
            BorderStyle.THIN
    );

    style.setBorderLeft(
            BorderStyle.THIN
    );

    style.setBorderRight(
            BorderStyle.THIN
    );

    return style;
}

// ================================================================
// EXCEL CELL HELPER
// ================================================================

private void setCell(
        Row row,
        int column,
        Object value
) {

    Cell cell =
            row.createCell(
                    column
            );

    if (value == null) {

        cell.setCellValue("");

    } else if (value instanceof Number number) {

        cell.setCellValue(
                number.doubleValue()
        );

    } else if (value instanceof Boolean bool) {

        cell.setCellValue(
                bool
        );

    } else {

        cell.setCellValue(
                value.toString()
        );
    }
}

// ================================================================
// AUTO SIZE
// ================================================================

private void autoSize(
        Sheet sheet,
        int columns
) {

    for (
            int i = 0;
            i < columns;
            i++
    ) {

        sheet.autoSizeColumn(
                i
        );
    }
}

// ================================================================
// LOANS EXCEL
// ================================================================

public byte[] exportLoansExcel(
        Long organizationId
) {

    List<Loan> loans =
            loanRepository.findByOrganization_Id(
                    organizationId
            );

    try (
            Workbook workbook =
                    new XSSFWorkbook();

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream()
    ) {

        Sheet sheet =
                workbook.createSheet(
                        "Loans"
                );

        CellStyle headerStyle =
                createHeaderStyle(
                        workbook
                );

        String[] headers = {
                "Reference",
                "Borrower",
                "Status",
                "Amount",
                "Currency",
                "Interest Rate",
                "Duration Months",
                "Outstanding Balance",
                "Loan Officer",
                "Branch",
                "Created At"
        };

        Row header =
                sheet.createRow(
                        0
                );

        for (
                int i = 0;
                i < headers.length;
                i++
        ) {

            Cell cell =
                    header.createCell(
                            i
                    );

            cell.setCellValue(
                    headers[i]
            );

            cell.setCellStyle(
                    headerStyle
            );
        }

        int rowNumber = 1;

        for (Loan l : loans) {

            Row row =
                    sheet.createRow(
                            rowNumber++
                    );

            setCell(
                    row,
                    0,
                    l.getReferenceNumber()
            );

            setCell(
                    row,
                    1,
                    l.getBorrower() != null
                            ? l.getBorrower().getFirstName()
                            + " "
                            + l.getBorrower().getLastName()
                            : ""
            );

            setCell(
                    row,
                    2,
                    l.getStatus()
            );

            setCell(
                    row,
                    3,
                    l.getAmount()
            );

            setCell(
                    row,
                    4,
                    l.getCurrency()
            );

            setCell(
                    row,
                    5,
                    l.getInterestRate()
            );

            setCell(
                    row,
                    6,
                    l.getDurationMonths()
            );

            setCell(
                    row,
                    7,
                    l.getOutstandingBalance()
            );

            setCell(
                    row,
                    8,
                    l.getLoanOfficer() != null
                            ? l.getLoanOfficer().getName()
                            : ""
            );

            setCell(
                    row,
                    9,
                    l.getBranch() != null
                            ? l.getBranch().getName()
                            : ""
            );

            setCell(
                    row,
                    10,
                    l.getCreatedAt()
            );
        }

        autoSize(
                sheet,
                headers.length
        );

        workbook.write(
                output
        );

        return output.toByteArray();

    } catch (IOException e) {

        throw new RuntimeException(
                "Failed to generate loans Excel report",
                e
        );
    }
}

// ================================================================
// PAYMENTS EXCEL
// ================================================================

public byte[] exportPaymentsExcel(
        Long organizationId
) {

    List<Payment> payments =
            paymentRepository.findByLoan_Organization_Id(
                    organizationId
            );

    try (
            Workbook workbook =
                    new XSSFWorkbook();

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream()
    ) {

        Sheet sheet =
                workbook.createSheet(
                        "Payments"
                );

        CellStyle headerStyle =
                createHeaderStyle(
                        workbook
                );

        String[] headers = {
                "Loan Reference",
                "Due Date",
                "Amount",
                "Penalty",
                "Paid",
                "Paid Date",
                "Payment Reference"
        };

        Row header =
                sheet.createRow(
                        0
                );

        for (
                int i = 0;
                i < headers.length;
                i++
        ) {

            Cell cell =
                    header.createCell(
                            i
                    );

            cell.setCellValue(
                    headers[i]
            );

            cell.setCellStyle(
                    headerStyle
            );
        }

        int rowNumber = 1;

        for (Payment p : payments) {

            Row row =
                    sheet.createRow(
                            rowNumber++
                    );

            setCell(
                    row,
                    0,
                    p.getLoan() != null
                            ? p.getLoan().getReferenceNumber()
                            : ""
            );

            setCell(
                    row,
                    1,
                    p.getDueDate()
            );

            setCell(
                    row,
                    2,
                    p.getAmount()
            );

            setCell(
                    row,
                    3,
                    p.getPenalty()
            );

            setCell(
                    row,
                    4,
                    p.getPaid()
            );

            setCell(
                    row,
                    5,
                    p.getPaidDate()
            );

            setCell(
                    row,
                    6,
                    p.getPaymentReference()
            );
        }

        autoSize(
                sheet,
                headers.length
        );

        workbook.write(
                output
        );

        return output.toByteArray();

    } catch (IOException e) {

        throw new RuntimeException(
                "Failed to generate payments Excel report",
                e
        );
    }
}

// ================================================================
// OVERDUE EXCEL
// ================================================================

public byte[] exportOverdueExcel(
        Long organizationId
) {

    LocalDate today =
            LocalDate.now();

    List<Payment> overdue =
            paymentRepository
                    .findByLoan_Organization_Id(
                            organizationId
                    )
                    .stream()
                    .filter(
                            p ->
                                    !Boolean.TRUE.equals(
                                            p.getPaid()
                                    )
                                            && p.getDueDate() != null
                                            && p.getDueDate()
                                            .isBefore(today)
                    )
                    .toList();

    try (
            Workbook workbook =
                    new XSSFWorkbook();

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream()
    ) {

        Sheet sheet =
                workbook.createSheet(
                        "Overdue Loans"
                );

        CellStyle headerStyle =
                createHeaderStyle(
                        workbook
                );

        String[] headers = {
                "Loan Reference",
                "Borrower",
                "Due Date",
                "Days Overdue",
                "Amount",
                "Penalty"
        };

        Row header =
                sheet.createRow(
                        0
                );

        for (
                int i = 0;
                i < headers.length;
                i++
        ) {

            Cell cell =
                    header.createCell(
                            i
                    );

            cell.setCellValue(
                    headers[i]
            );

            cell.setCellStyle(
                    headerStyle
            );
        }

        int rowNumber = 1;

        for (Payment p : overdue) {

            Loan l =
                    p.getLoan();

            long daysOverdue =
                    ChronoUnit.DAYS.between(
                            p.getDueDate(),
                            today
                    );

            Row row =
                    sheet.createRow(
                            rowNumber++
                    );

            setCell(
                    row,
                    0,
                    l != null
                            ? l.getReferenceNumber()
                            : ""
            );

            setCell(
                    row,
                    1,
                    l != null
                            && l.getBorrower() != null
                            ? l.getBorrower().getFirstName()
                            + " "
                            + l.getBorrower().getLastName()
                            : ""
            );

            setCell(
                    row,
                    2,
                    p.getDueDate()
            );

            setCell(
                    row,
                    3,
                    daysOverdue
            );

            setCell(
                    row,
                    4,
                    p.getAmount()
            );

            setCell(
                    row,
                    5,
                    p.getPenalty()
            );
        }

        autoSize(
                sheet,
                headers.length
        );

        workbook.write(
                output
        );

        return output.toByteArray();

    } catch (IOException e) {

        throw new RuntimeException(
                "Failed to generate overdue Excel report",
                e
        );
    }
}

// ================================================================
// PORTFOLIO SUMMARY EXCEL
// ================================================================

public byte[] exportPortfolioSummaryExcel(
        Long organizationId
) {

    Map<String, Long> statusCounts =
            loanStatusReport(
                    organizationId
            );

    Map<String, Double> payments =
            paymentReport(
                    organizationId
            );

    try (
            Workbook workbook =
                    new XSSFWorkbook();

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream()
    ) {

        Sheet sheet =
                workbook.createSheet(
                        "Portfolio Summary"
                );

        CellStyle headerStyle =
                createHeaderStyle(
                        workbook
                );

        Row header =
                sheet.createRow(
                        0
                );

        Cell metricHeader =
                header.createCell(
                        0
                );

        metricHeader.setCellValue(
                "Metric"
        );

        metricHeader.setCellStyle(
                headerStyle
        );

        Cell valueHeader =
                header.createCell(
                        1
                );

        valueHeader.setCellValue(
                "Value"
        );

        valueHeader.setCellStyle(
                headerStyle
        );

        int rowNumber = 1;

        for (
                Map.Entry<String, Long> entry
                        : statusCounts.entrySet()
        ) {

            Row row =
                    sheet.createRow(
                            rowNumber++
                    );

            setCell(
                    row,
                    0,
                    "Loans - "
                            + entry.getKey()
            );

            setCell(
                    row,
                    1,
                    entry.getValue()
            );
        }

        for (
                Map.Entry<String, Double> entry
                        : payments.entrySet()
        ) {

            Row row =
                    sheet.createRow(
                            rowNumber++
                    );

            setCell(
                    row,
                    0,
                    entry.getKey()
            );

            setCell(
                    row,
                    1,
                    entry.getValue()
            );
        }

        autoSize(
                sheet,
                2
        );

        workbook.write(
                output
        );

        return output.toByteArray();

    } catch (IOException e) {

        throw new RuntimeException(
                "Failed to generate portfolio Excel report",
                e
        );
    }
}


}
