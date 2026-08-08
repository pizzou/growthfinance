package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    // REPORTING DATA
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

    public Map<String, Double> paymentReport(Long organizationId) {

        List<Payment> payments =
                paymentRepository.findByLoan_Organization_Id(organizationId);

        double totalPaid = payments.stream()
                .filter(p -> Boolean.TRUE.equals(p.getPaid()))
                .mapToDouble(p ->
                        p.getAmount() != null ? p.getAmount() : 0.0
                )
                .sum();

        double totalPending = payments.stream()
                .filter(p -> !Boolean.TRUE.equals(p.getPaid()))
                .mapToDouble(p ->
                        p.getAmount() != null ? p.getAmount() : 0.0
                )
                .sum();

        double totalPenalties = payments.stream()
                .mapToDouble(p ->
                        p.getPenalty() != null ? p.getPenalty() : 0.0
                )
                .sum();

        return Map.of(
                "totalPaid", totalPaid,
                "totalPending", totalPending,
                "totalPenalties", totalPenalties
        );
    }

    // ============================================================
    // CSV HELPERS
    // ============================================================

    private String csvField(Object value) {

        if (value == null) {
            return "";
        }

        String valueString = value.toString();

        if (valueString.contains(",")
                || valueString.contains("\"")
                || valueString.contains("\n")
                || valueString.contains("\r")) {

            return "\"" +
                    valueString.replace("\"", "\"\"") +
                    "\"";
        }

        return valueString;
    }

    // ============================================================
    // CSV EXPORTS
    // ============================================================

    public String exportLoansCsv(Long organizationId) {

        List<Loan> loans =
                loanRepository.findByOrganization_Id(organizationId);

        StringBuilder csv = new StringBuilder();

        csv.append(
                "Reference,Borrower,Status,Amount,Currency," +
                "InterestRate,DurationMonths,OutstandingBalance," +
                "LoanOfficer,Branch,CreatedAt\n"
        );

        for (Loan loan : loans) {

            String borrower = "";

            if (loan.getBorrower() != null) {
                borrower =
                        safeString(loan.getBorrower().getFirstName())
                                + " "
                                + safeString(loan.getBorrower().getLastName());
            }

            String loanOfficer = "";

            if (loan.getLoanOfficer() != null) {
                loanOfficer =
                        safeString(loan.getLoanOfficer().getName());
            }

            String branch = "";

            if (loan.getBranch() != null) {
                branch =
                        safeString(loan.getBranch().getName());
            }

            csv.append(csvField(loan.getReferenceNumber())).append(",");
            csv.append(csvField(borrower)).append(",");
            csv.append(csvField(loan.getStatus())).append(",");
            csv.append(csvField(loan.getAmount())).append(",");
            csv.append(csvField(loan.getCurrency())).append(",");
            csv.append(csvField(loan.getInterestRate())).append(",");
            csv.append(csvField(loan.getDurationMonths())).append(",");
            csv.append(csvField(loan.getOutstandingBalance())).append(",");
            csv.append(csvField(loanOfficer)).append(",");
            csv.append(csvField(branch)).append(",");
            csv.append(csvField(loan.getCreatedAt())).append("\n");
        }

        return csv.toString();
    }

    public String exportPaymentsCsv(Long organizationId) {

        List<Payment> payments =
                paymentRepository.findByLoan_Organization_Id(organizationId);

        StringBuilder csv = new StringBuilder();

        csv.append(
                "LoanReference,DueDate,Amount,Penalty,Paid," +
                "PaidDate,PaymentReference\n"
        );

        for (Payment payment : payments) {

            String loanReference = "";

            if (payment.getLoan() != null) {
                loanReference =
                        safeString(
                                payment.getLoan().getReferenceNumber()
                        );
            }

            csv.append(csvField(loanReference)).append(",");
            csv.append(csvField(payment.getDueDate())).append(",");
            csv.append(csvField(payment.getAmount())).append(",");
            csv.append(csvField(payment.getPenalty())).append(",");
            csv.append(csvField(payment.getPaid())).append(",");
            csv.append(csvField(payment.getPaidDate())).append(",");
            csv.append(csvField(payment.getPaymentReference())).append("\n");
        }

        return csv.toString();
    }

    public String exportOverdueCsv(Long organizationId) {

        LocalDate today = LocalDate.now();

        List<Payment> overdue =
                paymentRepository
                        .findByLoan_Organization_Id(organizationId)
                        .stream()
                        .filter(payment ->
                                !Boolean.TRUE.equals(payment.getPaid())
                                        && payment.getDueDate() != null
                                        && payment.getDueDate().isBefore(today)
                        )
                        .toList();

        StringBuilder csv = new StringBuilder();

        csv.append(
                "LoanReference,Borrower,DueDate,DaysOverdue," +
                "Amount,Penalty\n"
        );

        for (Payment payment : overdue) {

            long daysOverdue =
                    ChronoUnit.DAYS.between(
                            payment.getDueDate(),
                            today
                    );

            Loan loan = payment.getLoan();

            String loanReference = "";
            String borrower = "";

            if (loan != null) {

                loanReference =
                        safeString(loan.getReferenceNumber());

                if (loan.getBorrower() != null) {

                    borrower =
                            safeString(
                                    loan.getBorrower().getFirstName()
                            )
                            + " "
                            + safeString(
                                    loan.getBorrower().getLastName()
                            );
                }
            }

            csv.append(csvField(loanReference)).append(",");
            csv.append(csvField(borrower)).append(",");
            csv.append(csvField(payment.getDueDate())).append(",");
            csv.append(csvField(daysOverdue)).append(",");
            csv.append(csvField(payment.getAmount())).append(",");
            csv.append(csvField(payment.getPenalty())).append("\n");
        }

        return csv.toString();
    }

    public String exportPortfolioSummaryCsv(Long organizationId) {

        Map<String, Long> statusCounts =
                loanStatusReport(organizationId);

        Map<String, Double> payments =
                paymentReport(organizationId);

        StringBuilder csv =
                new StringBuilder("Metric,Value\n");

        statusCounts.forEach((status, count) -> {

            csv.append(
                    csvField("Loans - " + status)
            )
            .append(",")
            .append(count)
            .append("\n");
        });

        payments.forEach((key, value) -> {

            csv.append(csvField(key))
                    .append(",")
                    .append(value)
                    .append("\n");
        });

        return csv.toString();
    }

    // ============================================================
    // EXCEL EXPORTS
    // ============================================================

    public byte[] exportLoansExcel(Long organizationId) {

        List<Loan> loans =
                loanRepository.findByOrganization_Id(organizationId);

        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet =
                    workbook.createSheet("Loans");

            CellStyle headerStyle =
                    createHeaderStyle(workbook);

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

            Row headerRow =
                    sheet.createRow(0);

            for (int i = 0; i < headers.length; i++) {

                Cell cell =
                        headerRow.createCell(i);

                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNumber = 1;

            for (Loan loan : loans) {

                Row row =
                        sheet.createRow(rowNumber++);

                String borrower = "";

                if (loan.getBorrower() != null) {
                    borrower =
                            safeString(
                                    loan.getBorrower().getFirstName()
                            )
                            + " "
                            + safeString(
                                    loan.getBorrower().getLastName()
                            );
                }

                String loanOfficer = "";

                if (loan.getLoanOfficer() != null) {
                    loanOfficer =
                            safeString(
                                    loan.getLoanOfficer().getName()
                            );
                }

                String branch = "";

                if (loan.getBranch() != null) {
                    branch =
                            safeString(
                                    loan.getBranch().getName()
                            );
                }

                setCell(row, 0, loan.getReferenceNumber());
                setCell(row, 1, borrower);
                setCell(row, 2, loan.getStatus());
                setCell(row, 3, loan.getAmount());
                setCell(row, 4, loan.getCurrency());
                setCell(row, 5, loan.getInterestRate());
                setCell(row, 6, loan.getDurationMonths());
                setCell(row, 7, loan.getOutstandingBalance());
                setCell(row, 8, loanOfficer);
                setCell(row, 9, branch);
                setCell(row, 10, loan.getCreatedAt());
            }

            autoSizeColumns(sheet, headers.length);

            return workbookToBytes(workbook);

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Failed to generate loans Excel report",
                    e
            );
        }
    }

    public byte[] exportPaymentsExcel(Long organizationId) {

        List<Payment> payments =
                paymentRepository
                        .findByLoan_Organization_Id(organizationId);

        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet =
                    workbook.createSheet("Payments");

            CellStyle headerStyle =
                    createHeaderStyle(workbook);

            String[] headers = {
                    "Loan Reference",
                    "Due Date",
                    "Amount",
                    "Penalty",
                    "Paid",
                    "Paid Date",
                    "Payment Reference"
            };

            Row headerRow =
                    sheet.createRow(0);

            for (int i = 0; i < headers.length; i++) {

                Cell cell =
                        headerRow.createCell(i);

                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNumber = 1;

            for (Payment payment : payments) {

                Row row =
                        sheet.createRow(rowNumber++);

                String loanReference = "";

                if (payment.getLoan() != null) {
                    loanReference =
                            safeString(
                                    payment.getLoan()
                                            .getReferenceNumber()
                            );
                }

                setCell(row, 0, loanReference);
                setCell(row, 1, payment.getDueDate());
                setCell(row, 2, payment.getAmount());
                setCell(row, 3, payment.getPenalty());
                setCell(row, 4, payment.getPaid());
                setCell(row, 5, payment.getPaidDate());
                setCell(row, 6, payment.getPaymentReference());
            }

            autoSizeColumns(sheet, headers.length);

            return workbookToBytes(workbook);

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Failed to generate payments Excel report",
                    e
            );
        }
    }

    public byte[] exportOverdueExcel(Long organizationId) {

        LocalDate today = LocalDate.now();

        List<Payment> overdue =
                paymentRepository
                        .findByLoan_Organization_Id(organizationId)
                        .stream()
                        .filter(payment ->
                                !Boolean.TRUE.equals(payment.getPaid())
                                        && payment.getDueDate() != null
                                        && payment.getDueDate().isBefore(today)
                        )
                        .toList();

        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet =
                    workbook.createSheet("Overdue Payments");

            CellStyle headerStyle =
                    createHeaderStyle(workbook);

            String[] headers = {
                    "Loan Reference",
                    "Borrower",
                    "Due Date",
                    "Days Overdue",
                    "Amount",
                    "Penalty"
            };

            Row headerRow =
                    sheet.createRow(0);

            for (int i = 0; i < headers.length; i++) {

                Cell cell =
                        headerRow.createCell(i);

                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNumber = 1;

            for (Payment payment : overdue) {

                Loan loan =
                        payment.getLoan();

                String loanReference = "";
                String borrower = "";

                if (loan != null) {

                    loanReference =
                            safeString(
                                    loan.getReferenceNumber()
                            );

                    if (loan.getBorrower() != null) {

                        borrower =
                                safeString(
                                        loan.getBorrower()
                                                .getFirstName()
                                )
                                + " "
                                + safeString(
                                        loan.getBorrower()
                                                .getLastName()
                                );
                    }
                }

                long daysOverdue =
                        ChronoUnit.DAYS.between(
                                payment.getDueDate(),
                                today
                        );

                Row row =
                        sheet.createRow(rowNumber++);

                setCell(row, 0, loanReference);
                setCell(row, 1, borrower);
                setCell(row, 2, payment.getDueDate());
                setCell(row, 3, daysOverdue);
                setCell(row, 4, payment.getAmount());
                setCell(row, 5, payment.getPenalty());
            }

            autoSizeColumns(sheet, headers.length);

            return workbookToBytes(workbook);

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Failed to generate overdue Excel report",
                    e
            );
        }
    }

    public byte[] exportPortfolioSummaryExcel(Long organizationId) {

        Map<String, Long> statusCounts =
                loanStatusReport(organizationId);

        Map<String, Double> payments =
                paymentReport(organizationId);

        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet =
                    workbook.createSheet("Portfolio Summary");

            CellStyle headerStyle =
                    createHeaderStyle(workbook);

            Row header =
                    sheet.createRow(0);

            Cell metricHeader =
                    header.createCell(0);

            metricHeader.setCellValue("Metric");
            metricHeader.setCellStyle(headerStyle);

            Cell valueHeader =
                    header.createCell(1);

            valueHeader.setCellValue("Value");
            valueHeader.setCellStyle(headerStyle);

            int rowNumber = 1;

            for (Map.Entry<String, Long> entry :
                    statusCounts.entrySet()) {

                Row row =
                        sheet.createRow(rowNumber++);

                setCell(
                        row,
                        0,
                        "Loans - " + entry.getKey()
                );

                setCell(
                        row,
                        1,
                        entry.getValue()
                );
            }

            for (Map.Entry<String, Double> entry :
                    payments.entrySet()) {

                Row row =
                        sheet.createRow(rowNumber++);

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

            autoSizeColumns(sheet, 2);

            return workbookToBytes(workbook);

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Failed to generate portfolio summary Excel report",
                    e
            );
        }
    }

    // ============================================================
    // EXCEL HELPERS
    // ============================================================

    private CellStyle createHeaderStyle(Workbook workbook) {

        CellStyle style =
                workbook.createCellStyle();

        Font font =
                workbook.createFont();

        font.setBold(true);

        style.setFont(font);

        style.setAlignment(
                HorizontalAlignment.CENTER
        );

        style.setFillPattern(
                FillPatternType.SOLID_FOREGROUND
        );

        return style;
    }

    private void setCell(
            Row row,
            int column,
            Object value) {

        Cell cell =
                row.createCell(column);

        if (value == null) {
            cell.setCellValue("");
            return;
        }

        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }

        if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
            return;
        }

        if (value instanceof LocalDate date) {
            cell.setCellValue(date.toString());
            return;
        }

        if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(dateTime.toString());
            return;
        }

        cell.setCellValue(value.toString());
    }

    private void autoSizeColumns(
            Sheet sheet,
            int numberOfColumns) {

        for (int i = 0; i < numberOfColumns; i++) {

            sheet.autoSizeColumn(i);

            int currentWidth =
                    sheet.getColumnWidth(i);

            int maxWidth =
                    256 * 50;

            if (currentWidth > maxWidth) {
                sheet.setColumnWidth(i, maxWidth);
            }
        }
    }

    private byte[] workbookToBytes(
            Workbook workbook) throws IOException {

        try (ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {

            workbook.write(output);

            return output.toByteArray();
        }
    }

    private String safeString(String value) {

        return value == null ? "" : value;
    }
}