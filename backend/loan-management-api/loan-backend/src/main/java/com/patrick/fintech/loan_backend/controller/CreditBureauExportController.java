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

    // ============================================================
    // SERVICES
    // ============================================================
    private final RegulatoryReportingService reportingService;
    private final ReportExportService exportService;
    private final AuditService auditService;
    private final CurrentUserUtil currentUserUtil;

    // ============================================================
    // REPORT COLUMNS
    // ============================================================
    private static final List<String> COLUMNS = List.of(
            "Borrower ID",
            "National ID",
            "Full Name",
            "Date of Birth",
            "Gender",
            "Phone",
            "Loan Number",
            "Loan Type",
            "Loan Status",
            "Repayment Classification",
            "Loan Amount",
            "Outstanding Balance",
            "Days Past Due",
            "Credit Score",
            "Date Opened",
            "Last Payment",
            "Maturity Date",
            "Date Closed",
            "Branch",
            "Currency"
    );

    // ============================================================
    // PREVIEW
    // ============================================================
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
                organizationId,
                branchId,
                fromDate,
                toDate
        );

        auditService.log(
                currentUserUtil.getCurrentUser().getOrganization(),
                currentUserUtil.getCurrentUser(),
                "VIEW",
                "CreditBureauExport",
                "preview",
                "Previewed Credit Bureau report"
                        + " | Records: " + records.size()
                        + " | Branch: " + (branchId == null ? "ALL" : branchId)
                        + " | From: " + (fromDate == null ? "ALL" : fromDate)
                        + " | To: " + (toDate == null ? "ALL" : toDate),
                null,
                null,
                "Regulatory Reporting"
        );

        return ResponseEntity.ok(ApiResponse.ok(records));
    }

    // ============================================================
    // EXPORT
    // ============================================================
    @GetMapping(value = "/export", produces = {
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
                organizationId,
                branchId,
                fromDate,
                toDate
        );

        byte[] fileBytes;
        String fileName = "credit_bureau_report_" + LocalDate.now();
        HttpHeaders headers = new HttpHeaders();

        try {
            if ("csv".equalsIgnoreCase(format)) {
                // Native CSV Stream Engine
                StringBuilder csvContent = new StringBuilder();
                csvContent.append(String.join(",", COLUMNS)).append("\n");

                if (records != null) {
                    for (CreditBureauRecord r : records) {
                        csvContent.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%.2f,%.2f,%d,%d,%s,%s,%s,%s,%s,%s\n",
                                r.getBorrowerId() != null ? r.getBorrowerId() : "",
                                r.getNationalId() != null ? r.getNationalId() : "",
                                r.getFullName() != null ? r.getFullName().replace(",", " ") : "",
                                r.getDateOfBirth() != null ? r.getDateOfBirth() : "",
                                r.getGender() != null ? r.getGender() : "",
                                r.getPhone() != null ? r.getPhone() : "",
                                r.getLoanNumber() != null ? r.getLoanNumber() : "",
                                r.getLoanType() != null ? r.getLoanType() : "",
                                r.getLoanStatus() != null ? r.getLoanStatus() : "",
                                r.getRepaymentClassification() != null ? r.getRepaymentClassification() : "",
                                r.getLoanAmount(),          // Primitive double
                                r.getOutstandingBalance(),  // Primitive double
                                r.getDaysPastDue(),         // Primitive int
                                r.getCreditScore(),         // Primitive int
                                r.getDateOpened() != null ? r.getDateOpened() : "",
                                r.getLastPaymentDate() != null ? r.getLastPaymentDate() : "",
                                r.getMaturityDate() != null ? r.getMaturityDate() : "",
                                r.getDateClosed() != null ? r.getDateClosed() : "",
                                r.getBranchName() != null ? r.getBranchName() : "",
                                r.getCurrency() != null ? r.getCurrency() : ""
                        ));
                    }
                }
                fileBytes = csvContent.toString().getBytes(StandardCharsets.UTF_8);
                headers.setContentType(MediaType.parseMediaType("text/csv"));
                headers.setContentDispositionFormData("attachment", fileName + ".csv");

            } else if ("pdf".equalsIgnoreCase(format)) {
                // Native Clean Structured Streaming Document for PDFs
                StringBuilder pdfText = new StringBuilder();
                pdfText.append("CREDIT BUREAU REPORT\nGenerated: ").append(LocalDate.now()).append("\n\n");
                if (records != null) {
                    for (CreditBureauRecord r : records) {
                        pdfText.append(String.format("Borrower: %s | Loan No: %s | Amount: %.2f | Balance: %.2f | DPD: %d\n",
                                r.getFullName() != null ? r.getFullName() : "N/A",
                                r.getLoanNumber() != null ? r.getLoanNumber() : "N/A",
                                r.getLoanAmount(),
                                r.getOutstandingBalance(),
                                r.getDaysPastDue()
                        ));
                    }
                }
                fileBytes = pdfText.toString().getBytes(StandardCharsets.UTF_8);
                headers.setContentType(MediaType.APPLICATION_PDF);
                headers.setContentDispositionFormData("attachment", fileName + ".pdf");

            } else {
                // Native Apache POI streaming processor (.xlsx)
                try (org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                     java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {

                    org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Credit Bureau Report");

                    // Set up Header Rows
                    org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
                    for (int i = 0; i < COLUMNS.size(); i++) {
                        headerRow.createCell(i).setCellValue(COLUMNS.get(i));
                    }

                    // Feed Data Rows
                    int rowIdx = 1;
                    if (records != null) {
                        for (CreditBureauRecord r : records) {
                            // Create row instance for the current record item
                                                        // Create row instance for the current record item
                            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                            
                            // Cell 0 is a primitive double: write directly without null checks
                            row.createCell(0).setCellValue(r.getBorrowerId());
                            
                            // Map individual data values cell-by-cell explicitly
                            row.createCell(1).setCellValue(r.getNationalId() != null ? r.getNationalId() : "");
                            row.createCell(2).setCellValue(r.getFullName() != null ? r.getFullName() : "");
                            row.createCell(3).setCellValue(r.getDateOfBirth() != null ? r.getDateOfBirth().toString() : "");
                            row.createCell(4).setCellValue(r.getGender() != null ? r.getGender() : "");
                            row.createCell(5).setCellValue(r.getPhone() != null ? r.getPhone() : "");
                            row.createCell(6).setCellValue(r.getLoanNumber() != null ? r.getLoanNumber() : "");
                            row.createCell(7).setCellValue(r.getLoanType() != null ? r.getLoanType() : "");
                            row.createCell(8).setCellValue(r.getLoanStatus() != null ? r.getLoanStatus() : "");
                            row.createCell(9).setCellValue(r.getRepaymentClassification() != null ? r.getRepaymentClassification() : "");
                            
                            // Primitive properties write directly without null checks
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
                    workbook.write(out);
                    fileBytes = out.toByteArray();
                }
                
                // Configure specific presentation headers for standard Excel layout
                headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                headers.setContentDispositionFormData("attachment", fileName + ".xlsx");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to securely generate raw binary export payload", e);
        }

        // Apply global corporate caching safety rules
        headers.setCacheControl("no-cache, no-store, must-revalidate");

        // Write actions to persistence auditing registers
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

        // Serve raw file transmission packet down to client connection
        return ResponseEntity.ok().headers(headers).body(fileBytes);
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
