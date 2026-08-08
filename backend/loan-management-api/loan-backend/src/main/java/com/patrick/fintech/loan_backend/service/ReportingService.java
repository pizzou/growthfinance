
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
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
                .collect(Collectors.groupingBy(
                        loan -> loan.getStatus().name(),
                        Collectors.counting()
                ));
    }

    // ============================================================
    // PAYMENT REPORT
    // ============================================================

    public Map<String, Double> paymentReport(Long organizationId) {

        List<Payment> payments =
                paymentRepository.findByLoan_Organization_Id(
                        organizationId
                );

        double totalPaid =
                payments.stream()
                        .filter(p ->
                                Boolean.TRUE.equals(
                                        p.getPaid()
                                )
                        )
                        .mapToDouble(p ->
                                p.getAmount() != null
                                        ? p.getAmount()
                                        : 0.0
                        )
                        .sum();

        double totalPending =
                payments.stream()
                        .filter(p ->
                                !Boolean.TRUE.equals(
                                        p.getPaid()
                                )
                        )
                        .mapToDouble(p ->
                                p.getAmount() != null
                                        ? p.getAmount()
                                        : 0.0
                        )
                        .sum();

        double totalPenalties =
                payments.stream()
                        .mapToDouble(p ->
                                p.getPenalty() != null
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

    // ============================================================
    // CSV HELPER
    // ============================================================

    private String csvField(Object value) {

        if (value == null) {
            return "";
        }

        String valueString =
                value.toString();

        if (valueString.contains(",")
                || valueString.contains("\"")
                || valueString.contains("\n")) {

            return "\""
                    + valueString.replace(
                            "\"",
                            "\"\""
                    )
                    + "\"";
        }

        return valueString;
    }

    // ============================================================
    // CSV - LOANS
    // ============================================================

    public String exportLoansCsv(
            Long organizationId) {

        List<Loan> loans =
                loanRepository.findByOrganization_Id(
                        organizationId
                );

        StringBuilder csv =
                new StringBuilder();

        csv.append(
                "Reference,Borrower,Status,Amount,Currency,"
                + "InterestRate,DurationMonths,OutstandingBalance,"
                + "LoanOfficer,Branch,CreatedAt\n"
        );

        for (Loan loan : loans) {

            String borrower =
                    loan.getBorrower() != null
                            ? loan.getBorrower().getFirstName()
                                + " "
                                + loan.getBorrower().getLastName()
                            : "";

            String loanOfficer =
                    loan.getLoanOfficer() != null
                            ? loan.getLoanOfficer().getName()
                            : "";

            String branch =
                    loan.getBranch() != null
                            ? loan.getBranch().getName()
                            : "";

            csv.append(
                    csvField(
                            loan.getReferenceNumber()
                    )
            ).append(",");

            csv.append(
                    csvField(borrower)
            ).append(",");

            csv.append(
                    csvField(loan.getStatus())
            ).append(",");

            csv.append(
                    csvField(loan.getAmount())
            ).append(",");

            csv.append(
                    csvField(loan.getCurrency())
            ).append(",");

            csv.append(
                    csvField(loan.getInterestRate())
            ).append(",");

            csv.append(
                    csvField(loan.getDurationMonths())
            ).append(",");

            csv.append(
                    csvField(
                            loan.getOutstandingBalance()
                    )
            ).append(",");

            csv.append(
                    csvField(loanOfficer)
            ).append(",");

            csv.append(
                    csvField(branch)
            ).append(",");

            csv.append(
                    csvField(loan.getCreatedAt())
            ).append("\n");
        }

        return csv.toString();
    }

    // ============================================================
    // CSV - PAYMENTS
    // ============================================================

    public String exportPaymentsCsv(
            Long organizationId) {

        List<Payment> payments =
                paymentRepository
                        .findByLoan_Organization_Id(
                                organizationId
                        );

        StringBuilder csv =
                new StringBuilder();

        csv.append(
                "LoanReference,DueDate,Amount,Penalty,Paid,"
                + "PaidDate,PaymentReference\n"
        );

        for (Payment payment : payments) {

            String loanReference =
                    payment.getLoan() != null
                            ? payment.getLoan()
                                .getReferenceNumber()
                            : "";

            csv.append(
                    csvField(loanReference)
            ).append(",");

            csv.append(
                    csvField(
                            payment.getDueDate()
                    )
            ).append(",");

            csv.append(
                    csvField(
                            payment.getAmount()
                    )
            ).append(",");

            csv.append(
                    csvField(
                            payment.getPenalty()
                    )
            ).append(",");

            csv.append(
                    csvField(
                            payment.getPaid()
                    )
            ).append(",");

            csv.append(
                    csvField(
                            payment.getPaidDate()
                    )
            ).append(",");

            csv.append(
                    csvField(
                            payment.getPaymentReference()
                    )
            ).append("\n");
        }

        return csv.toString();
    }

    // ============================================================
    // CSV - OVERDUE
    // ============================================================

    public String exportOverdueCsv(
            Long organizationId) {

        LocalDate today =
                LocalDate.now();

        List<Payment> overdue =
                paymentRepository
                        .findByLoan_Organization_Id(
                                organizationId
                        )
                        .stream()
                        .filter(payment ->
                                !Boolean.TRUE.equals(
                                        payment.getPaid()
                                )
                                && payment.getDueDate() != null
                                && payment.getDueDate()
                                        .isBefore(today)
                        )
                        .toList();

        StringBuilder csv =
                new StringBuilder();

        csv.append(
                "LoanReference,Borrower,DueDate,DaysOverdue,"
                + "Amount,Penalty\n"
        );

        for (Payment payment : overdue) {

            Loan loan =
                    payment.getLoan();

            String loanReference =
                    loan != null
                            ? loan.getReferenceNumber()
                            : "";

            String borrower =
                    loan != null
                            && loan.getBorrower() != null
                            ? loan.getBorrower().getFirstName()
                                + " "
                                + loan.getBorrower().getLastName()
                            : "";

            long daysOverdue =
                    ChronoUnit.DAYS.between(
                            payment.getDueDate(),
                            today
                    );

            csv.append(
                    csvField(loanReference)
            ).append(",");

            csv.append(
                    csvField(borrower)
            ).append(",");

            csv.append(
                    csvField(
                            payment.getDueDate()
                    )
            ).append(",");

            csv.append(
                    csvField(daysOverdue)
            ).append(",");

            csv.append(
                    csvField(
                            payment.getAmount()
                    )
            ).append(",");

            csv.append(
                    csvField(
                            payment.getPenalty()
                    )
            ).append("\n");
        }

        return csv.toString();
    }

    // ============================================================
    // CSV - PORTFOLIO SUMMARY
    // ============================================================

    public String exportPortfolioSummaryCsv(
            Long organizationId) {

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
                                csvField(
                                        "Loans - " + status
                                )
                        )
                        .append(",")
                        .append(count)
                        .append("\n")
        );

        payments.forEach(
                (key, value) ->
                        csv.append(
                                csvField(key)
                        )
                        .append(",")
                        .append(value)
                        .append("\n")
        );

        return csv.toString();
    }

    // ============================================================
    // EXCEL - COMMON STYLES
    // ============================================================

    /**
     * Excel header:
     *
     * BLACK BACKGROUND
     * WHITE TEXT
     *
     * This is intentionally kept because you requested
     * the black Excel header.
     */
    private CellStyle createHeaderStyle(
            XSSFWorkbook workbook) {

        CellStyle style =
                workbook.createCellStyle();

        style.setFillForegroundColor(
                IndexedColors.BLACK.getIndex()
        );

        style.setFillPattern(
                FillPatternType.SOLID_FOREGROUND
        );

        Font font =
                workbook.createFont();

        font.setBold(true);

        /*
         * IMPORTANT:
         * White text makes the black Excel header readable.
         */
        font.setColor(
                IndexedColors.WHITE.getIndex()
        );

        font.setFontHeightInPoints(
                (short) 11
        );

        style.setFont(font);

        style.setAlignment(
                HorizontalAlignment.CENTER
        );

        style.setVerticalAlignment(
                VerticalAlignment.CENTER
        );

        style.setBorderTop(
                BorderStyle.THIN
        );

        style.setBorderBottom(
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

    /**
     * Excel body style.
     *
     * Explicitly uses BLACK text so values such as
     * Loan Reference / Loan ID are always visible.
     */
    private CellStyle createBodyStyle(
            XSSFWorkbook workbook) {

        CellStyle style =
                workbook.createCellStyle();

        Font font =
                workbook.createFont();

        font.setColor(
                IndexedColors.BLACK.getIndex()
        );

        font.setFontHeightInPoints(
                (short) 10
        );

        style.setFont(font);

        style.setVerticalAlignment(
                VerticalAlignment.CENTER
        );

        style.setBorderBottom(
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

    // ============================================================
    // EXCEL - CURRENCY STYLE
    // ============================================================

    private CellStyle createCurrencyStyle(
            XSSFWorkbook workbook) {

        CellStyle style =
                createBodyStyle(workbook);

        style.setDataFormat(
                workbook
                        .createDataFormat()
                        .getFormat("#,##0.00")
        );

        return style;
    }

    // ============================================================
    // EXCEL - CELL HELPER
    // ============================================================

    private void setCell(
            Row row,
            int column,
            Object value,
            CellStyle style) {

        Cell cell =
                row.createCell(column);

        if (value == null) {

            cell.setCellValue("");

        } else if (value instanceof Number number) {

            cell.setCellValue(
                    number.doubleValue()
            );

        } else if (value instanceof Boolean bool) {

            cell.setCellValue(bool);

        } else {

            cell.setCellValue(
                    value.toString()
            );
        }

        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    // ============================================================
    // EXCEL - HEADER HELPER
    // ============================================================

    private void setHeader(
            Row row,
            String[] headers,
            CellStyle style) {

        for (int i = 0; i < headers.length; i++) {

            setCell(
                    row,
                    i,
                    headers[i],
                    style
            );
        }

        row.setHeightInPoints(24);
    }

    // ============================================================
    // EXCEL - COLUMN SIZING
    // ============================================================

    private void autoSizeColumns(
            Sheet sheet,
            int columnCount) {

        for (int i = 0; i < columnCount; i++) {

            sheet.autoSizeColumn(i);

            int currentWidth =
                    sheet.getColumnWidth(i);

            int minimumWidth =
                    3000;

            if (currentWidth < minimumWidth) {

                sheet.setColumnWidth(
                        i,
                        minimumWidth
                );
            }

            int maximumWidth =
                    12000;

            if (sheet.getColumnWidth(i)
                    > maximumWidth) {

                sheet.setColumnWidth(
                        i,
                        maximumWidth
                );
            }
        }
    }

    // ============================================================
    // EXCEL - LOANS
    // ============================================================

    public byte[] exportLoansExcel(
            Long organizationId) {

        List<Loan> loans =
                loanRepository.findByOrganization_Id(
                        organizationId
                );

        try (XSSFWorkbook workbook =
                     new XSSFWorkbook()) {

            Sheet sheet =
                    workbook.createSheet(
                            "Loan Portfolio"
                    );

            CellStyle headerStyle =
                    createHeaderStyle(workbook);

            CellStyle bodyStyle =
                    createBodyStyle(workbook);

            CellStyle currencyStyle =
                    createCurrencyStyle(workbook);

            String[] headers = {
                    "Reference",
                    "Borrower",
                    "Status",
                    "Amount",
                    "Currency",
                    "Interest Rate",
                    "Duration (Months)",
                    "Outstanding Balance",
                    "Loan Officer",
                    "Branch",
                    "Created At"
            };

            Row header =
                    sheet.createRow(0);

            setHeader(
                    header,
                    headers,
                    headerStyle
            );

            int rowNumber = 1;

            for (Loan loan : loans) {

                Row row =
                        sheet.createRow(
                                rowNumber++
                        );

                String borrower =
                        loan.getBorrower() != null
                                ? loan.getBorrower().getFirstName()
                                    + " "
                                    + loan.getBorrower().getLastName()
                                : "";

                String officer =
                        loan.getLoanOfficer() != null
                                ? loan.getLoanOfficer().getName()
                                : "";

                String branch =
                        loan.getBranch() != null
                                ? loan.getBranch().getName()
                                : "";

                setCell(
                        row,
                        0,
                        loan.getReferenceNumber(),
                        bodyStyle
                );

                setCell(
                        row,
                        1,
                        borrower,
                        bodyStyle
                );

                setCell(
                        row,
                        2,
                        loan.getStatus(),
                        bodyStyle
                );

                setCell(
                        row,
                        3,
                        loan.getAmount(),
                        currencyStyle
                );

                setCell(
                        row,
                        4,
                        loan.getCurrency(),
                        bodyStyle
                );

                setCell(
                        row,
                        5,
                        loan.getInterestRate(),
                        bodyStyle
                );

                setCell(
                        row,
                        6,
                        loan.getDurationMonths(),
                        bodyStyle
                );

                setCell(
                        row,
                        7,
                        loan.getOutstandingBalance(),
                        currencyStyle
                );

                setCell(
                        row,
                        8,
                        officer,
                        bodyStyle
                );

                setCell(
                        row,
                        9,
                        branch,
                        bodyStyle
                );

                setCell(
                        row,
                        10,
                        loan.getCreatedAt(),
                        bodyStyle
                );
            }

            sheet.createFreezePane(
                    0,
                    1
            );

            autoSizeColumns(
                    sheet,
                    headers.length
            );

            return workbookToBytes(workbook);

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to generate Loan Portfolio Excel report",
                    exception
            );
        }
    }

    // ============================================================
    // EXCEL - PAYMENTS
    // ============================================================

    public byte[] exportPaymentsExcel(
            Long organizationId) {

        List<Payment> payments =
                paymentRepository
                        .findByLoan_Organization_Id(
                                organizationId
                        );

        try (XSSFWorkbook workbook =
                     new XSSFWorkbook()) {

            Sheet sheet =
                    workbook.createSheet(
                            "Payment Register"
                    );

            CellStyle headerStyle =
                    createHeaderStyle(workbook);

            CellStyle bodyStyle =
                    createBodyStyle(workbook);

            CellStyle currencyStyle =
                    createCurrencyStyle(workbook);

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
                    sheet.createRow(0);

            setHeader(
                    header,
                    headers,
                    headerStyle
            );

            int rowNumber = 1;

            for (Payment payment : payments) {

                Row row =
                        sheet.createRow(
                                rowNumber++
                        );

                String loanReference =
                        payment.getLoan() != null
                                ? payment.getLoan()
                                    .getReferenceNumber()
                                : "";

                setCell(
                        row,
                        0,
                        loanReference,
                        bodyStyle
                );

                setCell(
                        row,
                        1,
                        payment.getDueDate(),
                        bodyStyle
                );

                setCell(
                        row,
                        2,
                        payment.getAmount(),
                        currencyStyle
                );

                setCell(
                        row,
                        3,
                        payment.getPenalty(),
                        currencyStyle
                );

                setCell(
                        row,
                        4,
                        payment.getPaid(),
                        bodyStyle
                );

                setCell(
                        row,
                        5,
                        payment.getPaidDate(),
                        bodyStyle
                );

                setCell(
                        row,
                        6,
                        payment.getPaymentReference(),
                        bodyStyle
                );
            }

            sheet.createFreezePane(
                    0,
                    1
            );

            autoSizeColumns(
                    sheet,
                    headers.length
            );

            return workbookToBytes(workbook);

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to generate Payment Register Excel report",
                    exception
            );
        }
    }

    // ============================================================
    // EXCEL - OVERDUE
    // ============================================================

    public byte[] exportOverdueExcel(
            Long organizationId) {

        LocalDate today =
                LocalDate.now();

        List<Payment> overdue =
                paymentRepository
                        .findByLoan_Organization_Id(
                                organizationId
                        )
                        .stream()
                        .filter(payment ->
                                !Boolean.TRUE.equals(
                                        payment.getPaid()
                                )
                                && payment.getDueDate() != null
                                && payment.getDueDate()
                                        .isBefore(today)
                        )
                        .toList();

        try (XSSFWorkbook workbook =
                     new XSSFWorkbook()) {

            Sheet sheet =
                    workbook.createSheet(
                            "Overdue Payments"
                    );

            CellStyle headerStyle =
                    createHeaderStyle(workbook);

            CellStyle bodyStyle =
                    createBodyStyle(workbook);

            CellStyle currencyStyle =
                    createCurrencyStyle(workbook);

            String[] headers = {
                    "Loan Reference",
                    "Borrower",
                    "Due Date",
                    "Days Overdue",
                    "Amount",
                    "Penalty"
            };

            Row header =
                    sheet.createRow(0);

            setHeader(
                    header,
                    headers,
                    headerStyle
            );

            int rowNumber = 1;

            for (Payment payment : overdue) {

                Row row =
                        sheet.createRow(
                                rowNumber++
                        );

                Loan loan =
                        payment.getLoan();

                String loanReference =
                        loan != null
                                ? loan.getReferenceNumber()
                                : "";

                String borrower =
                        loan != null
                                && loan.getBorrower() != null
                                ? loan.getBorrower().getFirstName()
                                    + " "
                                    + loan.getBorrower().getLastName()
                                : "";

                long daysOverdue =
                        ChronoUnit.DAYS.between(
                                payment.getDueDate(),
                                today
                        );

                setCell(
                        row,
                        0,
                        loanReference,
                        bodyStyle
                );

                setCell(
                        row,
                        1,
                        borrower,
                        bodyStyle
                );

                setCell(
                        row,
                        2,
                        payment.getDueDate(),
                        bodyStyle
                );

                setCell(
                        row,
                        3,
                        daysOverdue,
                        bodyStyle
                );

                setCell(
                        row,
                        4,
                        payment.getAmount(),
                        currencyStyle
                );

                setCell(
                        row,
                        5,
                        payment.getPenalty(),
                        currencyStyle
                );
            }

            sheet.createFreezePane(
                    0,
                    1
            );

            autoSizeColumns(
                    sheet,
                    headers.length
            );

            return workbookToBytes(workbook);

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to generate Overdue Payments Excel report",
                    exception
            );
        }
    }

    // ============================================================
    // EXCEL - PORTFOLIO SUMMARY
    // ============================================================

    public byte[] exportPortfolioSummaryExcel(
            Long organizationId) {

        Map<String, Long> statusCounts =
                loanStatusReport(
                        organizationId
                );

        Map<String, Double> paymentSummary =
                paymentReport(
                        organizationId
                );

        try (XSSFWorkbook workbook =
                     new XSSFWorkbook()) {

            Sheet sheet =
                    workbook.createSheet(
                            "Portfolio Summary"
                    );

            CellStyle headerStyle =
                    createHeaderStyle(workbook);

            CellStyle bodyStyle =
                    createBodyStyle(workbook);

            CellStyle currencyStyle =
                    createCurrencyStyle(workbook);

            String[] headers = {
                    "Metric",
                    "Value"
            };

            Row header =
                    sheet.createRow(0);

            setHeader(
                    header,
                    headers,
                    headerStyle
            );

            int rowNumber = 1;

            for (Map.Entry<String, Long> entry :
                    statusCounts.entrySet()) {

                Row row =
                        sheet.createRow(
                                rowNumber++
                        );

                setCell(
                        row,
                        0,
                        "Loans - "
                                + entry.getKey(),
                        bodyStyle
                );

                setCell(
                        row,
                        1,
                        entry.getValue(),
                        bodyStyle
                );
            }

            for (Map.Entry<String, Double> entry :
                    paymentSummary.entrySet()) {

                Row row =
                        sheet.createRow(
                                rowNumber++
                        );

                setCell(
                        row,
                        0,
                        entry.getKey(),
                        bodyStyle
                );

                setCell(
                        row,
                        1,
                        entry.getValue(),
                        currencyStyle
                );
            }

            sheet.createFreezePane(
                    0,
                    1
            );

            autoSizeColumns(
                    sheet,
                    headers.length
            );

            return workbookToBytes(workbook);

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to generate Portfolio Summary Excel report",
                    exception
            );
        }
    }

    // ============================================================
    // WORKBOOK -> BYTE[]
    // ============================================================

    private byte[] workbookToBytes(
            XSSFWorkbook workbook) {

        try (ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {

            workbook.write(output);

            return output.toByteArray();

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to write Excel workbook",
                    exception
            );
        }
    }
}
