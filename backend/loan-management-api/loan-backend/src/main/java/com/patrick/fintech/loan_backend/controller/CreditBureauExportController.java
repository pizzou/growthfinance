package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.CreditBureauRecord;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.RegulatoryReportingService;
import com.patrick.fintech.loan_backend.service.ReportExportService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/regulatory/credit-bureau")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','AUDITOR')")
public class CreditBureauExportController {

    private final RegulatoryReportingService reportingService;
    private final ReportExportService exportService;
    private final AuditService auditService;
    private final CurrentUserUtil currentUserUtil;

    private static final List<String> COLUMNS = List.of(
            "Borrower ID", "National ID", "Full Name", "Date of Birth", "Gender", "Phone",
            "Loan Number", "Loan Type", "Loan Status", "Repayment Classification", "Loan Amount",
            "Outstanding Balance", "Days Past Due", "Credit Score", "Date Opened", "Last Payment",
            "Maturity Date", "Date Closed", "Branch", "Currency"
    );

    @GetMapping("/preview")
    public ResponseEntity<ApiResponse<List<CreditBureauRecord>>> preview(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        Long organizationId = currentUserUtil.getCurrentOrganizationId();
        if (organizationId == null) {
            throw new IllegalStateException("Current user is not associated with an organization.");
        }

        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        validateDateRange(fromDate, toDate);

        List<CreditBureauRecord> records = reportingService.buildCreditBureauExport(
                organizationId, branchId, fromDate, toDate
        );

        auditService.log(
                currentUserUtil.getCurrentUser().getOrganization(),
                currentUserUtil.getCurrentUser(),
                "VIEW", "CreditBureauExport", "preview",
                "Previewed Credit Bureau report | Records: " + records.size(),
                null, null, "Regulatory Reporting"
        );

        return ResponseEntity.ok(ApiResponse.ok(records));
    }

    @GetMapping(value = "/download", produces = {
            MediaType.APPLICATION_OCTET_STREAM_VALUE,
            MediaType.APPLICATION_PDF_VALUE,
            "text/csv"
    })
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        Long organizationId = currentUserUtil.getCurrentOrganizationId();
        if (organizationId == null) {
            throw new IllegalStateException("Current user is not associated with an organization.");
        }

        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        validateDateRange(fromDate, toDate);

        List<CreditBureauRecord> records = reportingService.buildCreditBureauExport(
                organizationId, branchId, fromDate, toDate
        );

        byte[] fileBytes;
        String fileName = "credit_bureau_report_" + LocalDate.now();
        HttpHeaders headers = new HttpHeaders();

        try {
            if ("csv".equalsIgnoreCase(format)) {
                // ========================================================
                // NATIVE CSV ENGINE
                // ========================================================
                StringBuilder csv = new StringBuilder();
                csv.append(String.join(",", COLUMNS)).append("\n");

                if (records != null) {
                    for (CreditBureauRecord r : records) {
                        csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%.2f,%.2f,%d,%d,%s,%s,%s,%s,%s,%s\n",
                                r.getBorrowerId() != null ? String.valueOf(r.getBorrowerId()) : "",
                                r.getNationalId() != null ? r.getNationalId() : "",
                                r.getFullName() != null ? r.getFullName().replace(",", " ") : "",
                                r.getDateOfBirth() != null ? r.getDateOfBirth() : "",
                                r.getGender() != null ? r.getGender() : "",
                                r.getPhone() != null ? r.getPhone() : "",
                                r.getLoanNumber() != null ? r.getLoanNumber() : "",
                                r.getLoanType() != null ? r.getLoanType() : "",
                                r.getLoanStatus() != null ? r.getLoanStatus() : "",
                                r.getRepaymentClassification() != null ? r.getRepaymentClassification() : "",
                                r.getLoanAmount(), r.getOutstandingBalance(), r.getDaysPastDue(), 
                                r.getCreditScore() != null ? r.getCreditScore() : 0,
                                r.getDateOpened() != null ? r.getDateOpened() : "",
                                r.getLastPaymentDate() != null ? r.getLastPaymentDate() : "",
                                r.getMaturityDate() != null ? r.getMaturityDate() : "",
                                r.getDateClosed() != null ? r.getDateClosed() : "",
                                r.getBranchName() != null ? r.getBranchName() : "",
                                r.getCurrency() != null ? r.getCurrency() : ""
                        ));
                    }
                }
                fileBytes = csv.toString().getBytes(StandardCharsets.UTF_8);
                headers.setContentType(MediaType.parseMediaType("text/csv"));
                headers.setContentDispositionFormData("attachment", fileName + ".csv");

            } else if ("pdf".equalsIgnoreCase(format)) {
                // ========================================================
                // NATIVE FIXED PDF ENGINE (OpenPDF - Now supports all 20 columns)
                // ========================================================
                try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                    // Set Page Size to A3 Rotate (Landscape) to accommodate all 20 columns comfortably
                    com.lowagie.text.Document document = new com.lowagie.text.Document(com.lowagie.text.PageSize.A3.rotate());
                    com.lowagie.text.pdf.PdfWriter.getInstance(document, out);
                    document.open();

                    com.lowagie.text.Paragraph title = new com.lowagie.text.Paragraph("CREDIT BUREAU REGULATORY REPORT\nGenerated: " + LocalDate.now() + "\n\n");
                    title.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
                    document.add(title);

                    // Initialize OpenPDF Table with exactly 20 columns to match your DTO
                    com.lowagie.text.Table table = new com.lowagie.text.Table(20);
                    table.setPadding(3);
                    table.setWidth(100);

                    // Add all 20 headers dynamically from your static COLUMNS configuration
                    for (String columnName : COLUMNS) {
                        table.addCell(columnName);
                    }

                    // Feed Data Rows cleanly with complete properties mapping
                    if (records != null) {
                        for (CreditBureauRecord r : records) {
                            table.addCell(r.getBorrowerId() != null ? String.valueOf(r.getBorrowerId()) : "");
                            table.addCell(r.getNationalId() != null ? r.getNationalId() : "");
                            table.addCell(r.getFullName() != null ? r.getFullName() : "");
                            table.addCell(r.getDateOfBirth() != null ? r.getDateOfBirth().toString() : "");
                            table.addCell(r.getGender() != null ? r.getGender() : "");
                            table.addCell(r.getPhone() != null ? r.getPhone() : "");
                            table.addCell(r.getLoanNumber() != null ? r.getLoanNumber() : "");
                            table.addCell(r.getLoanType() != null ? r.getLoanType() : "");
                            table.addCell(r.getLoanStatus() != null ? r.getLoanStatus() : "");
                            table.addCell(r.getRepaymentClassification() != null ? r.getRepaymentClassification() : "");
                            table.addCell(String.format("%.2f", r.getLoanAmount()));
                            table.addCell(String.format("%.2f", r.getOutstandingBalance()));
                            table.addCell(String.valueOf(r.getDaysPastDue()));
                            table.addCell(r.getCreditScore() != null ? String.valueOf(r.getCreditScore()) : "");
                            table.addCell(r.getDateOpened() != null ? r.getDateOpened().toString() : "");
                            table.addCell(r.getLastPaymentDate() != null ? r.getLastPaymentDate().toString() : "");
                            // Add trailing cells to finish out the OpenPDF table matrix
                            table.addCell(r.getMaturityDate() != null ? r.getMaturityDate().toString() : "");
                            table.addCell(r.getDateClosed() != null ? r.getDateClosed().toString() : "");
                            table.addCell(r.getBranchName() != null ? r.getBranchName() : "");
                            table.addCell(r.getCurrency() != null ? r.getCurrency() : "");
                        }
                    }

                    // Append table to PDF layout and close stream buffers safely
                    document.add(table);
                    document.close();
                    fileBytes = out.toByteArray();
                }

                headers.setContentType(MediaType.APPLICATION_PDF);
                headers.setContentDispositionFormData("attachment", fileName + ".pdf");

            } else {
                // ========================================================
                // NATIVE EXCEL (.XLSX) ENGINE
                // ========================================================
                try (org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                     java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                    
                    org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Credit Bureau Report");
                    org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
                    
                    for (int i = 0; i < COLUMNS.size(); i++) {
                        headerRow.createCell(i).setCellValue(COLUMNS.get(i));
                    }

                    int rowIdx = 1;
                    if (records != null) {
                        for (CreditBureauRecord r : records) {
                            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                            
                            // Map all 20 columns cleanly to Excel cell indexes row by row
                            row.createCell(0).setCellValue(r.getBorrowerId() != null ? r.getBorrowerId() : 0L);
                            row.createCell(1).setCellValue(r.getNationalId() != null ? r.getNationalId() : "");
                            row.createCell(2).setCellValue(r.getFullName() != null ? r.getFullName() : "");
                            row.createCell(3).setCellValue(r.getDateOfBirth() != null ? r.getDateOfBirth().toString() : "");
                            row.createCell(4).setCellValue(r.getGender() != null ? r.getGender() : "");
                            row.createCell(5).setCellValue(r.getPhone() != null ? r.getPhone() : "");
                            row.createCell(6).setCellValue(r.getLoanNumber() != null ? r.getLoanNumber() : "");
                            row.createCell(7).setCellValue(r.getLoanType() != null ? r.getLoanType() : "");
                            row.createCell(8).setCellValue(r.getLoanStatus() != null ? r.getLoanStatus() : "");
                            row.createCell(9).setCellValue(r.getRepaymentClassification() != null ? r.getRepaymentClassification() : "");
                            row.createCell(10).setCellValue(r.getLoanAmount());
                            row.createCell(11).setCellValue(r.getOutstandingBalance());
                            row.createCell(12).setCellValue(r.getDaysPastDue());
                            row.createCell(13).setCellValue(r.getCreditScore() != null ? r.getCreditScore() : 0);
                            row.createCell(14).setCellValue(r.getDateOpened() != null ? r.getDateOpened().toString() : "");
                            row.createCell(15).setCellValue(r.getLastPaymentDate() != null ? r.getLastPaymentDate().toString() : "");
                            row.createCell(16).setCellValue(r.getMaturityDate() != null ? r.getMaturityDate().toString() : "");
                            row.createCell(17).setCellValue(r.getDateClosed() != null ? r.getDateClosed().toString() : "");
                            row.createCell(18).setCellValue(r.getBranchName() != null ? r.getBranchName() : "");
                            row.createCell(19).setCellValue(r.getCurrency() != null ? r.getCurrency() : "");
                        }
                    }
                    
                    wb.write(out);
                    fileBytes = out.toByteArray();
                }

                headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                headers.setContentDispositionFormData("attachment", fileName + ".xlsx");
            }

        } catch (Exception e) {
            throw new IllegalStateException("Failed to securely generate raw binary export payload", e);
        }

        // Apply strict corporate anti-cache headers
        headers.setCacheControl("no-cache, no-store, must-revalidate");

        // Commit execution parameters to system audit tables
        auditService.log(
                currentUserUtil.getCurrentUser().getOrganization(),
                currentUserUtil.getCurrentUser(),
                "EXPORT",
                "CreditBureauExport",
                "export",
                "Exported Credit Bureau report as " + format.toUpperCase(),
                null,
                null,
                "Regulatory Reporting"
        );

        // Serve raw file transmission bundle down to connection pipeline
        return ResponseEntity.ok()
                .headers(headers)
                .body(fileBytes);
    }

    // ============================================================
    // PRIVATE UTILS
    // ============================================================

    private LocalDate parseDate(String d) {
        return d != null && !d.trim().isEmpty() ? LocalDate.parse(d) : null;
    }

    private void validateDateRange(LocalDate f, LocalDate t) {
        if (f != null && t != null && f.isAfter(t)) {
            throw new IllegalArgumentException("From date cannot be after To date.");
        }
    }
}
