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
                                String.valueOf(r.getBorrowerId()),
                                r.getNationalId() != null ? r.getNationalId() : "",
                                r.getFullName() != null ? r.getFullName().replace(",", " ") : "",
                                r.getDateOfBirth() != null ? r.getDateOfBirth() : "",
                                r.getGender() != null ? r.getGender() : "",
                                r.getPhone() != null ? r.getPhone() : "",
                                r.getLoanNumber() != null ? r.getLoanNumber() : "",
                                r.getLoanType() != null ? r.getLoanType() : "",
                                r.getLoanStatus() != null ? r.getLoanStatus() : "",
                                r.getRepaymentClassification() != null ? r.getRepaymentClassification() : "",
                                r.getLoanAmount(), r.getOutstandingBalance(), r.getDaysPastDue(), r.getCreditScore(),
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
                // NATIVE TRUE PDF ENGINE (OpenPDF)
                // ========================================================
                try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                    com.lowagie.text.Document document = new com.lowagie.text.Document(com.lowagie.text.PageSize.A4.rotate());
                    com.lowagie.text.pdf.PdfWriter.getInstance(document, out);
                    document.open();

                    com.lowagie.text.Paragraph title = new com.lowagie.text.Paragraph("CREDIT BUREAU REGULATORY REPORT\nGenerated: " + LocalDate.now() + "\n\n");
                    title.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
                    document.add(title);

                    // Creating table layout matching targeted parameters
                    com.lowagie.text.Table table = new com.lowagie.text.Table(5);
                    table.addCell("Full Name");
                    table.addCell("Loan Number");
                    table.addCell("Loan Amount");
                    table.addCell("Outstanding Balance");
                    table.addCell("Days Past Due");

                    if (records != null) {
                        for (CreditBureauRecord r : records) {
                            table.addCell(r.getFullName() != null ? r.getFullName() : "");
                            table.addCell(r.getLoanNumber() != null ? r.getLoanNumber() : "");
                            table.addCell(String.format("%.2f", r.getLoanAmount()));
                            table.addCell(String.format("%.2f", r.getOutstandingBalance()));
                            table.addCell(String.valueOf(r.getDaysPastDue()));
                        }
                    }

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
                            row.createCell(0).setCellValue(r.getBorrowerId());
                            row.createCell(1).setCellValue(r.getNationalId() != null ? r.getNationalId() : "");
                            // Map individual text-based fields safely
                            row.createCell(2).setCellValue(r.getFullName() != null ? r.getFullName() : "");
                            row.createCell(3).setCellValue(r.getDateOfBirth() != null ? r.getDateOfBirth().toString() : "");
                            row.createCell(4).setCellValue(r.getGender() != null ? r.getGender() : "");
                            row.createCell(5).setCellValue(r.getPhone() != null ? r.getPhone() : "");
                            row.createCell(6).setCellValue(r.getLoanNumber() != null ? r.getLoanNumber() : "");
                            row.createCell(7).setCellValue(r.getLoanType() != null ? r.getLoanType() : "");
                            row.createCell(8).setCellValue(r.getLoanStatus() != null ? r.getLoanStatus() : "");
                            row.createCell(9).setCellValue(r.getRepaymentClassification() != null ? r.getRepaymentClassification() : "");
                            
                            // Map primitive numerical fields directly without null checks
                            row.createCell(10).setCellValue(r.getLoanAmount());
                            row.createCell(11).setCellValue(r.getOutstandingBalance());
                            row.createCell(12).setCellValue(r.getDaysPastDue());
                            row.createCell(13).setCellValue(r.getCreditScore());
                            
                            // Map trailing date details and metadata properties safely
                            row.createCell(14).setCellValue(r.getDateOpened() != null ? r.getDateOpened().toString() : "");
                            row.createCell(15).setCellValue(r.getLastPaymentDate() != null ? r.getLastPaymentDate().toString() : "");
                            row.createCell(16).setCellValue(r.getMaturityDate() != null ? r.getMaturityDate().toString() : "");
                            row.createCell(17).setCellValue(r.getDateClosed() != null ? r.getDateClosed().toString() : "");
                            row.createCell(18).setCellValue(r.getBranchName() != null ? r.getBranchName() : "");
                            row.createCell(19).setCellValue(r.getCurrency() != null ? r.getCurrency() : "");
                        }
                    }

                    // Flush internal workbook buffers to output binary stream
                    wb.write(out);
                    fileBytes = out.toByteArray();
                }

                // Configure presentation headers for Excel format
                headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                headers.setContentDispositionFormData("attachment", fileName + ".xlsx");
            }

        } catch (Exception e) {
            throw new IllegalStateException("Failed to securely generate raw binary export payload", e);
        }

        // Apply global security anti-caching headers
        headers.setCacheControl("no-cache, no-store, must-revalidate");

        // Log actions to persistence auditing registry
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

        // Return the binary file stream directly to the client connection
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
