
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;

import lombok.extern.slf4j.Slf4j;

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
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
public class ReportingService {

    private final LoanRepository loanRepository;
    private final PaymentRepository paymentRepository;

    /*
     * Financial calculations use BigDecimal.
     *
     * Money is presented with two decimal places.
     */
    private static final int MONEY_SCALE = 2;

    private static final RoundingMode MONEY_ROUNDING =
            RoundingMode.HALF_UP;

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    MONEY_ROUNDING
            );

    private static final BigDecimal BALANCE_TOLERANCE =
            new BigDecimal("0.01");

    public ReportingService(
            LoanRepository loanRepository,
            PaymentRepository paymentRepository
    ) {
        this.loanRepository = Objects.requireNonNull(
                loanRepository,
                "LoanRepository is required"
        );

        this.paymentRepository = Objects.requireNonNull(
                paymentRepository,
                "PaymentRepository is required"
        );
    }

    // ============================================================
    // LOAN STATUS REPORT
    // ============================================================

    public Map<String, Long> loanStatusReport(
            Long organizationId
    ) {

        validateOrganizationId(organizationId);

        List<Loan> loans =
                safeLoans(
                        loanRepository.findByOrganization_Id(
                                organizationId
                        )
                );

        return loans.stream()
                .filter(Objects::nonNull)
                .filter(loan -> loan.getStatus() != null)
                .collect(
                        Collectors.groupingBy(
                                loan ->
                                        loan.getStatus().name(),
                                LinkedHashMap::new,
                                Collectors.counting()
                        )
                );
    }

    // ============================================================
    // PAYMENT REPORT
    // ============================================================

    /**
     * Returns payment totals using BigDecimal.
     *
     * IMPORTANT:
     *
     * Do not use:
     *
     * payment.getAmount()
     * payment.getPenalty()
     *
     * because those are legacy Double getters.
     *
     * The authoritative financial values are:
     *
     * payment.getAmountDecimal()
     * payment.getPenaltyDecimal()
     */
    public Map<String, BigDecimal> paymentReport(
            Long organizationId
    ) {

        validateOrganizationId(organizationId);

        List<Payment> payments =
                safePayments(
                        paymentRepository
                                .findByLoan_Organization_Id(
                                        organizationId
                                )
                );

        BigDecimal totalPaid =
                ZERO;

        BigDecimal totalPending =
                ZERO;

        BigDecimal totalPenalties =
                ZERO;

        for (Payment payment : payments) {

            if (payment == null) {
                continue;
            }

            BigDecimal amount =
                    normalizeMoney(
                            payment.getAmountDecimal()
                    );

            BigDecimal penalty =
                    normalizeMoney(
                            payment.getPenaltyDecimal()
                    );

            if (
                    Boolean.TRUE.equals(
                            payment.getPaid()
                    )
            ) {

                totalPaid =
                        add(
                                totalPaid,
                                amount
                        );

            } else {

                totalPending =
                        add(
                                totalPending,
                                amount
                        );
            }

            totalPenalties =
                    add(
                            totalPenalties,
                            penalty
                    );
        }

        Map<String, BigDecimal> result =
                new LinkedHashMap<>();

        result.put(
                "totalPaid",
                totalPaid
        );

        result.put(
                "totalPending",
                totalPending
        );

        result.put(
                "totalPenalties",
                totalPenalties
        );

        return result;
    }

    // ============================================================
    // CSV FIELD
    // ============================================================

    private String csvField(
            Object value
    ) {

        if (value == null) {
            return "";
        }

        String valueString =
                value.toString();

        if (
                valueString.contains(",")
                        || valueString.contains("\"")
                        || valueString.contains("\n")
                        || valueString.contains("\r")
        ) {

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
            Long organizationId
    ) {

        validateOrganizationId(organizationId);

        List<Loan> loans =
                safeLoans(
                        loanRepository.findByOrganization_Id(
                                organizationId
                        )
                );

        StringBuilder csv =
                new StringBuilder(
                        4096
                );

        csv.append(
                "Reference,Borrower,Status,Amount,Currency,"
                        + "InterestRate,DurationMonths,OutstandingBalance,"
                        + "LoanOfficer,Branch,CreatedAt\n"
        );

        for (Loan loan : loans) {

            if (loan == null) {
                continue;
            }

            String borrower =
                    borrowerName(loan);

            String loanOfficer =
                    loanOfficerName(loan);

            String branch =
                    branchName(loan);

            csv.append(
                    csvField(
                            loan.getReferenceNumber()
                    )
            ).append(",");

            csv.append(
                    csvField(
                            borrower
                    )
            ).append(",");

            csv.append(
                    csvField(
                            loan.getStatus() != null
                                    ? loan.getStatus().name()
                                    : ""
                    )
            ).append(",");

            /*
             * BigDecimal getter.
             */
            csv.append(
                    csvField(
                            normalizeMoney(
                                    loan.getAmountDecimal()
                            )
                    )
            ).append(",");

            csv.append(
                    csvField(
                            loan.getCurrency()
                    )
            ).append(",");

            csv.append(
                    csvField(
                            loan.getInterestRateDecimal()
                    )
            ).append(",");

            csv.append(
                    csvField(
                            loan.getDurationMonths()
                    )
            ).append(",");

            /*
             * BigDecimal getter.
             */
            csv.append(
                    csvField(
                            normalizeMoney(
                                    loan.getOutstandingBalanceDecimal()
                            )
                    )
            ).append(",");

            csv.append(
                    csvField(
                            loanOfficer
                    )
            ).append(",");

            csv.append(
                    csvField(
                            branch
                    )
            ).append(",");

            csv.append(
                    csvField(
                            loan.getCreatedAt()
                    )
            ).append("\n");
        }

        return csv.toString();
    }

    // ============================================================
    // CSV - PAYMENTS
    // ============================================================

    public String exportPaymentsCsv(
            Long organizationId
    ) {

        validateOrganizationId(organizationId);

        List<Payment> payments =
                safePayments(
                        paymentRepository
                                .findByLoan_Organization_Id(
                                        organizationId
                                )
                );

        StringBuilder csv =
                new StringBuilder(
                        4096
                );

        csv.append(
                "LoanReference,DueDate,Amount,Penalty,Paid,"
                        + "PaidDate,PaymentReference\n"
        );

        for (Payment payment : payments) {

            if (payment == null) {
                continue;
            }

            String loanReference =
                    payment.getLoan() != null
                            ? payment.getLoan()
                                    .getReferenceNumber()
                            : "";

            csv.append(
                    csvField(
                            loanReference
                    )
            ).append(",");

            csv.append(
                    csvField(
                            payment.getDueDate()
                    )
            ).append(",");

            /*
             * BigDecimal getter.
             */
            csv.append(
                    csvField(
                            normalizeMoney(
                                    payment.getAmountDecimal()
                            )
                    )
            ).append(",");

            /*
             * BigDecimal getter.
             */
            csv.append(
                    csvField(
                            normalizeMoney(
                                    payment.getPenaltyDecimal()
                            )
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
            Long organizationId
    ) {

        validateOrganizationId(organizationId);

        LocalDate today =
                LocalDate.now();

        List<Payment> payments =
                safePayments(
                        paymentRepository
                                .findByLoan_Organization_Id(
                                        organizationId
                                )
                );

        List<Payment> overdue =
                payments.stream()
                        .filter(Objects::nonNull)
                        .filter(
                                payment ->
                                        !Boolean.TRUE.equals(
                                                payment.getPaid()
                                        )
                        )
                        .filter(
                                payment ->
                                        payment.getDueDate() != null
                        )
                        .filter(
                                payment ->
                                        payment.getDueDate()
                                                .isBefore(today)
                        )
                        .toList();

        StringBuilder csv =
                new StringBuilder(
                        4096
                );

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
                            ? borrowerName(loan)
                            : "";

            long daysOverdue =
                    ChronoUnit.DAYS.between(
                            payment.getDueDate(),
                            today
                    );

            csv.append(
                    csvField(
                            loanReference
                    )
            ).append(",");

            csv.append(
                    csvField(
                            borrower
                    )
            ).append(",");

            csv.append(
                    csvField(
                            payment.getDueDate()
                    )
            ).append(",");

            csv.append(
                    csvField(
                            daysOverdue
                    )
            ).append(",");

            csv.append(
                    csvField(
                            normalizeMoney(
                                    payment.getAmountDecimal()
                            )
                    )
            ).append(",");

            csv.append(
                    csvField(
                            normalizeMoney(
                                    payment.getPenaltyDecimal()
                            )
                    )
            ).append("\n");
        }

        return csv.toString();
    }

    // ============================================================
    // CSV - PORTFOLIO SUMMARY
    // ============================================================

    public String exportPortfolioSummaryCsv(
            Long organizationId
    ) {

        validateOrganizationId(organizationId);

        Map<String, Long> statusCounts =
                loanStatusReport(
                        organizationId
                );

        Map<String, BigDecimal> payments =
                paymentReport(
                        organizationId
                );

        StringBuilder csv =
                new StringBuilder(
                        4096
                );

        csv.append(
                "Metric,Value\n"
        );

        statusCounts.forEach(
                (status, count) -> {

                    csv.append(
                            csvField(
                                    "Loans - " + status
                            )
                    )
                    .append(",")
                    .append(
                            count
                    )
                    .append("\n");
                }
        );

        payments.forEach(
                (key, value) -> {

                    csv.append(
                            csvField(
                                    key
                            )
                    )
                    .append(",")
                    .append(
                            normalizeMoney(value)
                    )
                    .append("\n");
                }
        );

        return csv.toString();
    }

    // ============================================================
    // EXCEL - HEADER STYLE
    // ============================================================

    private CellStyle createHeaderStyle(
            XSSFWorkbook workbook
    ) {

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

    // ============================================================
    // EXCEL - BODY STYLE
    // ============================================================

    private CellStyle createBodyStyle(
            XSSFWorkbook workbook
    ) {

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
            XSSFWorkbook workbook
    ) {

        CellStyle style =
                createBodyStyle(
                        workbook
                );

        style.setDataFormat(
                workbook
                        .createDataFormat()
                        .getFormat(
                                "#,##0.00"
                        )
        );

        style.setAlignment(
                HorizontalAlignment.RIGHT
        );

        return style;
    }

    // ============================================================
    // EXCEL - PERCENTAGE STYLE
    // ============================================================

    private CellStyle createPercentageStyle(
            XSSFWorkbook workbook
    ) {

        CellStyle style =
                createBodyStyle(
                        workbook
                );

        style.setDataFormat(
                workbook
                        .createDataFormat()
                        .getFormat(
                                "0.00"
                        )
        );

        style.setAlignment(
                HorizontalAlignment.RIGHT
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
            CellStyle style
    ) {

        Cell cell =
                row.createCell(
                        column
                );

        if (value == null) {

            cell.setCellValue("");

        } else if (
                value instanceof BigDecimal decimal
        ) {

            /*
             * Apache POI ultimately stores a numeric Excel cell
             * as a double, but the financial calculation itself
             * remains BigDecimal.
             *
             * The conversion happens only at the Excel output
             * boundary.
             */
            cell.setCellValue(
                    decimal.doubleValue()
            );

        } else if (
                value instanceof Number number
        ) {

            cell.setCellValue(
                    number.doubleValue()
            );

        } else if (
                value instanceof Boolean bool
        ) {

            cell.setCellValue(
                    bool
            );

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
            CellStyle style
    ) {

        for (
                int i = 0;
                i < headers.length;
                i++
        ) {

            setCell(
                    row,
                    i,
                    headers[i],
                    style
            );
        }

        row.setHeightInPoints(
                24
        );
    }

    // ============================================================
    // EXCEL - COLUMN SIZING
    // ============================================================

    private void autoSizeColumns(
            Sheet sheet,
            int columnCount
    ) {

        for (
                int i = 0;
                i < columnCount;
                i++
        ) {

            try {

                sheet.autoSizeColumn(i);

            } catch (RuntimeException exception) {

                log.warn(
                        "Unable to auto-size Excel column {}",
                        i,
                        exception
                );
            }

            int currentWidth =
                    sheet.getColumnWidth(i);

            int minimumWidth =
                    3000;

            if (
                    currentWidth
                            < minimumWidth
            ) {

                sheet.setColumnWidth(
                        i,
                        minimumWidth
                );
            }

            int maximumWidth =
                    12000;

            if (
                    sheet.getColumnWidth(i)
                            > maximumWidth
            ) {

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
            Long organizationId
    ) {

        validateOrganizationId(
                organizationId
        );

        List<Loan> loans =
                safeLoans(
                        loanRepository.findByOrganization_Id(
                                organizationId
                        )
                );

        try (
                XSSFWorkbook workbook =
                        new XSSFWorkbook()
        ) {

            Sheet sheet =
                    workbook.createSheet(
                            "Loan Portfolio"
                    );

            CellStyle headerStyle =
                    createHeaderStyle(
                            workbook
                    );

            CellStyle bodyStyle =
                    createBodyStyle(
                            workbook
                    );

            CellStyle currencyStyle =
                    createCurrencyStyle(
                            workbook
                    );

            CellStyle percentageStyle =
                    createPercentageStyle(
                            workbook
                    );

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

            int rowNumber =
                    1;

            for (Loan loan : loans) {

                if (loan == null) {
                    continue;
                }

                Row row =
                        sheet.createRow(
                                rowNumber++
                        );

                String borrower =
                        borrowerName(loan);

                String officer =
                        loanOfficerName(loan);

                String branch =
                        branchName(loan);

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
                        loan.getStatus() != null
                                ? loan.getStatus().name()
                                : "",
                        bodyStyle
                );

                setCell(
                        row,
                        3,
                        normalizeMoney(
                                loan.getAmountDecimal()
                        ),
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
                        loan.getInterestRateDecimal(),
                        percentageStyle
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
                        normalizeMoney(
                                loan.getOutstandingBalanceDecimal()
                        ),
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

            return workbookToBytes(
                    workbook
            );

        } catch (IOException exception) {

            log.error(
                    "Failed to generate Loan Portfolio Excel report for organization {}",
                    organizationId,
                    exception
            );

            throw new IllegalStateException(
                    "Failed to generate Loan Portfolio Excel report",
                    exception
            );
        } catch (RuntimeException exception) {

            log.error(
                    "Unexpected error while generating Loan Portfolio Excel report for organization {}",
                    organizationId,
                    exception
            );

            throw new IllegalStateException(
                    "Unable to generate Loan Portfolio Excel report",
                    exception
            );
        }
    }

    // ============================================================
    // EXCEL - PAYMENTS
    // ============================================================

    public byte[] exportPaymentsExcel(
            Long organizationId
    ) {

        validateOrganizationId(
                organizationId
        );

        List<Payment> payments =
                safePayments(
                        paymentRepository
                                .findByLoan_Organization_Id(
                                        organizationId
                                )
                );

        try (
                XSSFWorkbook workbook =
                        new XSSFWorkbook()
        ) {

            Sheet sheet =
                    workbook.createSheet(
                            "Payment Register"
                    );

            CellStyle headerStyle =
                    createHeaderStyle(
                            workbook
                    );

            CellStyle bodyStyle =
                    createBodyStyle(
                            workbook
                    );

            CellStyle currencyStyle =
                    createCurrencyStyle(
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
                    sheet.createRow(0);

            setHeader(
                    header,
                    headers,
                    headerStyle
            );

            int rowNumber =
                    1;

            for (Payment payment : payments) {

                if (payment == null) {
                    continue;
                }

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
                        normalizeMoney(
                                payment.getAmountDecimal()
                        ),
                        currencyStyle
                );

                setCell(
                        row,
                        3,
                        normalizeMoney(
                                payment.getPenaltyDecimal()
                        ),
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

            return workbookToBytes(
                    workbook
            );

        } catch (IOException exception) {

            log.error(
                    "Failed to generate Payment Register Excel report for organization {}",
                    organizationId,
                    exception
            );

            throw new IllegalStateException(
                    "Failed to generate Payment Register Excel report",
                    exception
            );
        } catch (RuntimeException exception) {

            log.error(
                    "Unexpected error while generating Payment Register Excel report for organization {}",
                    organizationId,
                    exception
            );

            throw new IllegalStateException(
                    "Unable to generate Payment Register Excel report",
                    exception
            );
        }
    }

    // ============================================================
    // EXCEL - OVERDUE
    // ============================================================

    public byte[] exportOverdueExcel(
            Long organizationId
    ) {

        validateOrganizationId(
                organizationId
        );

        LocalDate today =
                LocalDate.now();

        List<Payment> payments =
                safePayments(
                        paymentRepository
                                .findByLoan_Organization_Id(
                                        organizationId
                                )
                );

        List<Payment> overdue =
                payments.stream()
                        .filter(Objects::nonNull)
                        .filter(
                                payment ->
                                        !Boolean.TRUE.equals(
                                                payment.getPaid()
                                        )
                        )
                        .filter(
                                payment ->
                                        payment.getDueDate() != null
                        )
                        .filter(
                                payment ->
                                        payment.getDueDate()
                                                .isBefore(today)
                        )
                        .toList();

        try (
                XSSFWorkbook workbook =
                        new XSSFWorkbook()
        ) {

            Sheet sheet =
                    workbook.createSheet(
                            "Overdue Payments"
                    );

            CellStyle headerStyle =
                    createHeaderStyle(
                            workbook
                    );

            CellStyle bodyStyle =
                    createBodyStyle(
                            workbook
                    );

            CellStyle currencyStyle =
                    createCurrencyStyle(
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
                    sheet.createRow(0);

            setHeader(
                    header,
                    headers,
                    headerStyle
            );

            int rowNumber =
                    1;

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
                                ? borrowerName(loan)
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
                        normalizeMoney(
                                payment.getAmountDecimal()
                        ),
                        currencyStyle
                );

                setCell(
                        row,
                        5,
                        normalizeMoney(
                                payment.getPenaltyDecimal()
                        ),
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

            return workbookToBytes(
                    workbook
            );

        } catch (IOException exception) {

            log.error(
                    "Failed to generate Overdue Payments Excel report for organization {}",
                    organizationId,
                    exception
            );

            throw new IllegalStateException(
                    "Failed to generate Overdue Payments Excel report",
                    exception
            );
        } catch (RuntimeException exception) {

            log.error(
                    "Unexpected error while generating Overdue Payments Excel report for organization {}",
                    organizationId,
                    exception
            );

            throw new IllegalStateException(
                    "Unable to generate Overdue Payments Excel report",
                    exception
            );
        }
    }

    // ============================================================
    // EXCEL - PORTFOLIO SUMMARY
    // ============================================================

    public byte[] exportPortfolioSummaryExcel(
            Long organizationId
    ) {

        validateOrganizationId(
                organizationId
        );

        Map<String, Long> statusCounts =
                loanStatusReport(
                        organizationId
                );

        Map<String, BigDecimal> paymentSummary =
                paymentReport(
                        organizationId
                );

        try (
                XSSFWorkbook workbook =
                        new XSSFWorkbook()
        ) {

            Sheet sheet =
                    workbook.createSheet(
                            "Portfolio Summary"
                    );

            CellStyle headerStyle =
                    createHeaderStyle(
                            workbook
                    );

            CellStyle bodyStyle =
                    createBodyStyle(
                            workbook
                    );

            CellStyle currencyStyle =
                    createCurrencyStyle(
                            workbook
                    );

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

            int rowNumber =
                    1;

            for (
                    Map.Entry<String, Long> entry :
                    statusCounts.entrySet()
            ) {

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

            for (
                    Map.Entry<String, BigDecimal> entry :
                    paymentSummary.entrySet()
            ) {

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
                        normalizeMoney(
                                entry.getValue()
                        ),
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

            return workbookToBytes(
                    workbook
            );

        } catch (IOException exception) {

            log.error(
                    "Failed to generate Portfolio Summary Excel report for organization {}",
                    organizationId,
                    exception
            );

            throw new IllegalStateException(
                    "Failed to generate Portfolio Summary Excel report",
                    exception
            );
        } catch (RuntimeException exception) {

            log.error(
                    "Unexpected error while generating Portfolio Summary Excel report for organization {}",
                    organizationId,
                    exception
            );

            throw new IllegalStateException(
                    "Unable to generate Portfolio Summary Excel report",
                    exception
            );
        }
    }

    // ============================================================
    // WORKBOOK -> BYTE[]
    // ============================================================

    private byte[] workbookToBytes(
            XSSFWorkbook workbook
    ) {

        if (workbook == null) {

            throw new IllegalArgumentException(
                    "Workbook cannot be null"
            );
        }

        try (
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream(16 * 1024)
        ) {

            workbook.write(
                    output
            );

            output.flush();

            byte[] bytes =
                    output.toByteArray();

            if (
                    bytes.length == 0
            ) {

                throw new IllegalStateException(
                        "Generated Excel workbook is empty"
                );
            }

            log.debug(
                    "Generated Excel workbook successfully: {} bytes",
                    bytes.length
            );

            return bytes;

        } catch (IOException exception) {

            log.error(
                    "Failed to serialize Excel workbook",
                    exception
            );

            throw new IllegalStateException(
                    "Failed to write Excel workbook",
                    exception
            );
        }
    }

    // ============================================================
    // BORROWER NAME
    // ============================================================

    private String borrowerName(
            Loan loan
    ) {

        if (
                loan == null
                        || loan.getBorrower() == null
        ) {
            return "";
        }

        String firstName =
                loan.getBorrower().getFirstName() != null
                        ? loan.getBorrower().getFirstName().trim()
                        : "";

        String lastName =
                loan.getBorrower().getLastName() != null
                        ? loan.getBorrower().getLastName().trim()
                        : "";

        return (
                firstName
                        + " "
                        + lastName
        ).trim();
    }

    // ============================================================
    // LOAN OFFICER NAME
    // ============================================================

    private String loanOfficerName(
            Loan loan
    ) {

        if (
                loan == null
                        || loan.getLoanOfficer() == null
        ) {
            return "";
        }

        String name =
                loan.getLoanOfficer().getName();

        return name != null
                ? name.trim()
                : "";
    }

    // ============================================================
    // BRANCH NAME
    // ============================================================

    private String branchName(
            Loan loan
    ) {

        if (
                loan == null
                        || loan.getBranch() == null
        ) {
            return "";
        }

        String name =
                loan.getBranch().getName();

        return name != null
                ? name.trim()
                : "";
    }

    // ============================================================
    // SAFE LOAN LIST
    // ============================================================

    private List<Loan> safeLoans(
            List<Loan> loans
    ) {

        if (loans == null) {
            return new ArrayList<>();
        }

        return loans;
    }

    // ============================================================
    // SAFE PAYMENT LIST
    // ============================================================

    private List<Payment> safePayments(
            List<Payment> payments
    ) {

        if (payments == null) {
            return new ArrayList<>();
        }

        return payments;
    }

    // ============================================================
    // BIGDECIMAL ADD
    // ============================================================

    private BigDecimal add(
            BigDecimal first,
            BigDecimal second
    ) {

        BigDecimal a =
                first == null
                        ? ZERO
                        : first;

        BigDecimal b =
                second == null
                        ? ZERO
                        : second;

        return normalizeMoney(
                a.add(b)
        );
    }

    // ============================================================
    // BIGDECIMAL SUBTRACT
    // ============================================================

    private BigDecimal subtract(
            BigDecimal first,
            BigDecimal second
    ) {

        BigDecimal a =
                first == null
                        ? ZERO
                        : first;

        BigDecimal b =
                second == null
                        ? ZERO
                        : second;

        return normalizeMoney(
                a.subtract(b)
        );
    }

    // ============================================================
    // NORMALIZE MONEY
    // ============================================================

    private BigDecimal normalizeMoney(
            BigDecimal value
    ) {

        if (value == null) {
            return ZERO;
        }

        return value.setScale(
                MONEY_SCALE,
                MONEY_ROUNDING
        );
    }

    // ============================================================
    // MATERIAL VALUE
    // ============================================================

    private boolean isMaterial(
            BigDecimal value
    ) {

        if (value == null) {
            return false;
        }

        return value
                .abs()
                .compareTo(
                        BALANCE_TOLERANCE
                ) >= 0;
    }

    // ============================================================
    // ORGANIZATION VALIDATION
    // ============================================================

    private void validateOrganizationId(
            Long organizationId
    ) {

        if (organizationId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required."
            );
        }

        if (organizationId <= 0) {

            throw new IllegalArgumentException(
                    "Organization ID must be greater than zero."
            );
        }
    }
}
