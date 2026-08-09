package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.dto.ImportRowResult;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.ImportBatch;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.ImportBatchRepository;
import com.patrick.fintech.loan_backend.util.LedgerFileParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ================================================================
 * LEGACY LOAN IMPORT SERVICE
 * ================================================================
 *
 * Production-oriented entry point for importing historical loan
 * ledgers from CSV / Excel files.
 *
 * Responsibilities:
 *
 * 1. Parse the uploaded ledger.
 * 2. Validate basic import context.
 * 3. Support safe preview mode.
 * 4. Create an auditable ImportBatch for committed imports.
 * 5. Delegate each row to LegacyLoanImportRowService.
 * 6. Keep each row transactionally independent.
 * 7. Record successful and failed rows.
 * 8. Update batch status correctly.
 * 9. Produce an audit trail.
 *
 * IMPORTANT:
 *
 * LegacyLoanImportRowService owns the REQUIRES_NEW transaction
 * for each individual row.
 *
 * Therefore:
 *
 * Row 1 succeeds
 * Row 2 succeeds
 * Row 3 fails
 * Row 4 succeeds
 *
 * Rows 1, 2 and 4 remain committed.
 *
 * Row 3 is reported as failed.
 *
 * ================================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LegacyLoanImportService {

    /*
     * ================================================================
     * CONFIGURATION
     * ================================================================
     */

    /**
     * Maximum number of rows allowed in one import.
     *
     * This protects the application from accidental or malicious
     * extremely large uploads.
     *
     * If your business requires larger imports, increase this value
     * deliberately and consider asynchronous/background processing.
     */
    private static final int MAX_IMPORT_ROWS = 10_000;

    /**
     * Maximum filename length stored in the database.
     */
    private static final int MAX_FILENAME_LENGTH = 255;

    /**
     * Maximum number of rows returned from preview/commit processing
     * that we retain in the in-memory result collection.
     *
     * This is intentionally equal to MAX_IMPORT_ROWS because the
     * import itself is capped at that size.
     */
    private static final int MAX_RESULTS = MAX_IMPORT_ROWS;

    /**
     * Import batch statuses.
     *
     * Keep these values consistent with the ImportBatch entity and
     * any frontend/reporting code consuming them.
     */
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_FAILED = "FAILED";

    private final LegacyLoanImportRowService rowService;

    private final ImportBatchRepository importBatchRepo;

    private final AuditService auditService;

    private final ObjectMapper objectMapper;


    /*
     * ================================================================
     * PREVIEW
     * ================================================================
     *
     * Preview performs exactly the same row validation/business logic
     * as commit, but does not persist borrowers or loans.
     *
     * It does NOT create an ImportBatch.
     */
    @Transactional(readOnly = true)
    public List<ImportRowResult> preview(
            String filename,
            InputStream in,
            Organization org
    ) throws IOException {

        validateImportRequest(
                filename,
                in,
                org,
                null
        );

        String safeFilename =
                normalizeFilename(filename);

        List<Map<String, String>> rows;

        try {

            rows =
                    LedgerFileParser.parse(
                            safeFilename,
                            in
                    );

        } catch (IOException e) {

            log.error(
                    "Legacy loan preview file parsing failed. " +
                            "organizationId={}, filename={}",
                    org.getId(),
                    safeFilename,
                    e
            );

            throw new IOException(
                    "The uploaded ledger could not be read. " +
                            "Please verify that the file is a valid CSV or Excel ledger.",
                    e
            );
        }

        validateParsedRows(
                rows
        );

        Map<String, Borrower> sessionBorrowers =
                new HashMap<>();

        List<ImportRowResult> results =
                new ArrayList<>(
                        Math.min(
                                rows.size(),
                                MAX_RESULTS
                        )
                );

        long startedAt =
                System.currentTimeMillis();

        int rowNumber =
                1;

        for (
                Map<String, String> row :
                rows
        ) {

            rowNumber++;

            ImportRowResult result =
                    rowService.importRow(
                            row,
                            rowNumber,
                            org,
                            null,
                            false,
                            sessionBorrowers
                    );

            results.add(
                    result
            );
        }

        long durationMs =
                System.currentTimeMillis() - startedAt;

        long successful =
                results.stream()
                        .filter(
                                ImportRowResult::isSuccess
                        )
                        .count();

        long failed =
                results.size() - successful;

        log.info(
                "Legacy loan preview completed. " +
                        "organizationId={}, filename={}, rows={}, successful={}, failed={}, durationMs={}",
                org.getId(),
                safeFilename,
                results.size(),
                successful,
                failed,
                durationMs
        );

        return results;
    }


    /*
     * ================================================================
     * COMMIT
     * ================================================================
     *
     * The ImportBatch itself is persisted first as PROCESSING.
     *
     * Each individual row is then processed by
     * LegacyLoanImportRowService using REQUIRES_NEW.
     *
     * The final batch status is updated after all rows have been
     * attempted.
     */
    public ImportBatch commit(
            String filename,
            InputStream in,
            Organization org,
            User importedBy
    ) throws IOException {

        validateImportRequest(
                filename,
                in,
                org,
                importedBy
        );

        String safeFilename =
                normalizeFilename(filename);

        List<Map<String, String>> rows;

        try {

            rows =
                    LedgerFileParser.parse(
                            safeFilename,
                            in
                    );

        } catch (IOException e) {

            log.error(
                    "Legacy loan commit file parsing failed. " +
                            "organizationId={}, filename={}",
                    org.getId(),
                    safeFilename,
                    e
            );

            throw new IOException(
                    "The uploaded ledger could not be read. " +
                            "Please verify that the file is a valid CSV or Excel ledger.",
                    e
            );
        }

        validateParsedRows(
                rows
        );

        /*
         * ============================================================
         * CREATE IMPORT BATCH
         * ============================================================
         *
         * Never create the batch as COMPLETED before processing.
         */
        ImportBatch batch =
                ImportBatch.builder()
                        .organization(org)
                        .importedBy(importedBy)
                        .fileName(safeFilename)
                        .totalRows(rows.size())
                        .successCount(0)
                        .failureCount(0)
                        .status(STATUS_PROCESSING)
                        .build();

        batch =
                importBatchRepo.save(
                        batch
                );

        final Long batchId =
                batch.getId();

        log.info(
                "Legacy loan import started. " +
                        "organizationId={}, batchId={}, filename={}, totalRows={}, importedBy={}",
                org.getId(),
                batchId,
                safeFilename,
                rows.size(),
                importedBy.getId()
        );

        Map<String, Borrower> sessionBorrowers =
                new HashMap<>();

        List<ImportRowResult> results =
                new ArrayList<>(
                        Math.min(
                                rows.size(),
                                MAX_RESULTS
                        )
                );

        long startedAt =
                System.currentTimeMillis();

        int rowNumber =
                1;

        int success =
                0;

        int failed =
                0;

        /*
         * ============================================================
         * PROCESS EVERY ROW
         * ============================================================
         */
        for (
                Map<String, String> row :
                rows
        ) {

            rowNumber++;

            ImportRowResult result;

            try {

                result =
                        rowService.importRow(
                                row,
                                rowNumber,
                                org,
                                batchId,
                                true,
                                sessionBorrowers
                        );

            } catch (Exception e) {

                /*
                 * Defensive protection.
                 *
                 * Normally LegacyLoanImportRowService catches and
                 * converts row-level failures to ImportRowResult.
                 *
                 * This extra guard ensures one unexpected exception
                 * does not stop the entire import loop.
                 */
                log.error(
                        "Unexpected exception while processing legacy loan import row. " +
                                "organizationId={}, batchId={}, rowNumber={}",
                        org.getId(),
                        batchId,
                        rowNumber,
                        e
                );

                result =
                        ImportRowResult.builder()
                                .rowNumber(rowNumber)
                                .success(false)
                                .error(
                                        "Unexpected error while processing this row. " +
                                                "The row was not imported."
                                )
                                .build();
            }

            results.add(
                    result
            );

            if (
                    result.isSuccess()
            ) {

                success++;

            } else {

                failed++;
            }
        }

        long durationMs =
                System.currentTimeMillis() - startedAt;

        /*
         * ============================================================
         * FINALIZE BATCH
         * ============================================================
         */

        String finalStatus;

        if (
                failed == 0
        ) {

            finalStatus =
                    STATUS_COMPLETED;

        } else if (
                success == 0
        ) {

            finalStatus =
                    STATUS_FAILED;

        } else {

            finalStatus =
                    STATUS_PARTIAL;
        }

        batch.setSuccessCount(
                success
        );

        batch.setFailureCount(
                failed
        );

        batch.setStatus(
                finalStatus
        );

        /*
         * Store detailed row results for staff review.
         *
         * If serialization fails, the import itself remains valid.
         */
        try {

            batch.setRowResults(
                    objectMapper.writeValueAsString(
                            results
                    )
            );

        } catch (Exception e) {

            log.error(
                    "Unable to serialize legacy loan import row results. " +
                            "organizationId={}, batchId={}",
                    org.getId(),
                    batchId,
                    e
            );

            /*
             * Do not destroy the import merely because the optional
             * staff-review JSON could not be generated.
             */
            batch.setRowResults(
                    null
            );
        }

        batch =
                importBatchRepo.save(
                        batch
                );

        /*
         * ============================================================
         * AUDIT
         * ============================================================
         */

        try {

            String auditMessage =
                    "Imported " +
                            success +
                            "/" +
                            results.size() +
                            " rows from \"" +
                            safeFilename +
                            "\"";

            if (
                    failed > 0
            ) {

                auditMessage +=
                        " (" +
                                failed +
                                " row(s) failed — see batch detail)";
            }

            auditMessage +=
                    ". Batch status: " +
                            finalStatus +
                            ".";

            auditService.log(
                    org,
                    importedBy,
                    "LEGACY_LOANS_IMPORTED",
                    "IMPORT_BATCH",
                    batch.getId().toString(),
                    auditMessage
            );

        } catch (Exception e) {

            /*
             * Audit failure should be logged loudly.
             *
             * Whether you want an audit failure to fail the import
             * itself depends on your compliance policy.
             *
             * For this service we preserve the financial import and
             * make the audit failure highly visible in logs.
             */
            log.error(
                    "Legacy loan import completed but audit logging failed. " +
                            "organizationId={}, batchId={}",
                    org.getId(),
                    batch.getId(),
                    e
            );
        }

        log.info(
                "Legacy loan import completed. " +
                        "organizationId={}, batchId={}, filename={}, totalRows={}, " +
                        "success={}, failed={}, status={}, durationMs={}",
                org.getId(),
                batch.getId(),
                safeFilename,
                results.size(),
                success,
                failed,
                finalStatus,
                durationMs
        );

        return batch;
    }


    /*
     * ================================================================
     * VALIDATE IMPORT REQUEST
     * ================================================================
     */
    private void validateImportRequest(
            String filename,
            InputStream in,
            Organization org,
            User importedBy
    ) {

        if (
                org == null
        ) {

            throw new IllegalArgumentException(
                    "Organization is required for legacy loan import."
            );
        }

        if (
                org.getId() == null
        ) {

            throw new IllegalArgumentException(
                    "Organization ID is required for legacy loan import."
            );
        }

        if (
                in == null
        ) {

            throw new IllegalArgumentException(
                    "No ledger file was supplied."
            );
        }

        if (
                filename == null
                        || filename.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "A ledger filename is required."
            );
        }

        if (
                importedBy != null
                        && importedBy.getId() == null
        ) {

            throw new IllegalArgumentException(
                    "The importing user must have a valid ID."
            );
        }
    }


    /*
     * ================================================================
     * PARSED ROW VALIDATION
     * ================================================================
     */
    private void validateParsedRows(
            List<Map<String, String>> rows
    ) {

        if (
                rows == null
                        || rows.isEmpty()
        ) {

            throw new IllegalArgumentException(
                    "The uploaded ledger contains no data rows."
            );
        }

        if (
                rows.size() > MAX_IMPORT_ROWS
        ) {

            throw new IllegalArgumentException(
                    "The uploaded ledger contains " +
                            rows.size() +
                            " rows. The maximum allowed per import is " +
                            MAX_IMPORT_ROWS +
                            ". Please split the ledger into smaller files."
            );
        }

        for (
                int i = 0;
                i < rows.size();
                i++
        ) {

            if (
                    rows.get(i) == null
            ) {

                throw new IllegalArgumentException(
                        "Import row " +
                                (i + 2) +
                                " is empty."
                );
            }
        }
    }


    /*
     * ================================================================
     * FILENAME NORMALIZATION
     * ================================================================
     *
     * Do not store arbitrary client-provided filenames without
     * normalization.
     */
    private String normalizeFilename(
            String filename
    ) {

        String normalized =
                filename
                        .trim()
                        .replace(
                                "\\",
                                "_"
                        )
                        .replace(
                                "/",
                                "_"
                        );

        if (
                normalized.isBlank()
        ) {

            normalized =
                    "legacy-loan-import.csv";
        }

        if (
                normalized.length() > MAX_FILENAME_LENGTH
        ) {

            normalized =
                    normalized.substring(
                            0,
                            MAX_FILENAME_LENGTH
                    );
        }

        return normalized;
    }


    /*
     * ================================================================
     * CSV TEMPLATE
     * ================================================================
     *
     * This template intentionally uses obviously synthetic data.
     *
     * IMPORTANT:
     *
     * The example National ID is NOT a real customer identity.
     */
    public String buildCsvTemplate() {

        return String.join(
                ",",
                "national_id",
                "first_name",
                "last_name",
                "email",
                "phone",
                "gender",
                "marital_status",
                "loan_type",
                "amount",
                "interest_rate",
                "interest_rate_type",
                "duration_months",
                "start_date",
                "status",
                "total_paid",
                "outstanding_balance",
                "currency",
                "loan_reference",
                "notes"
        )
                + "\n"
                +
                String.join(
                        ",",
                        "SYNTHETIC-ID-0001",
                        "Jean",
                        "Uwimana",
                        "",
                        "0788000000",
                        "Male",
                        "Married",
                        "PERSONAL",
                        "500000",
                        "10",
                        "MONTHLY",
                        "6",
                        "2025-01-15",
                        "ACTIVE",
                        "150000",
                        "",
                        "RWF",
                        "OLD-LEDGER-EXAMPLE-0042",
                        "Synthetic example - replace with real historical data"
                );
    }
}