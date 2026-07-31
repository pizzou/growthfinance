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
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Entry point for bulk-importing a client's pre-existing manual (Excel/CSV) loan ledger.
 * Two modes over the same row logic (see LegacyLoanImportRowService):
 *   - preview(): validates every row and reports what WOULD happen, nothing is saved.
 *   - commit(): actually creates the borrowers/loans, row by row, each in its own
 *     transaction — one bad row doesn't undo the rows around it — and records an
 *     ImportBatch so the run is auditable afterwards.
 */
@Service
@RequiredArgsConstructor
public class LegacyLoanImportService {

    private final LegacyLoanImportRowService rowService;
    private final ImportBatchRepository importBatchRepo;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public List<ImportRowResult> preview(String filename, InputStream in, Organization org) throws IOException {
        List<Map<String, String>> rows = LedgerFileParser.parse(filename, in);
        Map<String, Borrower> sessionBorrowers = new HashMap<>();
        List<ImportRowResult> results = new ArrayList<>();
        int rowNum = 1;
        for (Map<String, String> row : rows) {
            rowNum++; // header is row 1
            results.add(rowService.importRow(row, rowNum, org, null, false, sessionBorrowers));
        }
        return results;
    }

    public ImportBatch commit(String filename, InputStream in, Organization org, User importedBy) throws IOException {
        List<Map<String, String>> rows = LedgerFileParser.parse(filename, in);

        ImportBatch batch = ImportBatch.builder()
            .organization(org).importedBy(importedBy).fileName(filename)
            .totalRows(rows.size()).status("COMPLETED")
            .build();
        batch = importBatchRepo.save(batch);

        Map<String, Borrower> sessionBorrowers = new HashMap<>();
        List<ImportRowResult> results = new ArrayList<>();
        int rowNum = 1;
        for (Map<String, String> row : rows) {
            rowNum++;
            results.add(rowService.importRow(row, rowNum, org, batch.getId(), true, sessionBorrowers));
        }

        int success = (int) results.stream().filter(ImportRowResult::isSuccess).count();
        int failed  = results.size() - success;

        batch.setSuccessCount(success);
        batch.setFailureCount(failed);
        batch.setStatus(failed == 0 ? "COMPLETED" : (success == 0 ? "FAILED" : "PARTIAL"));
        try { batch.setRowResults(objectMapper.writeValueAsString(results)); }
        catch (Exception ignored) { /* row results are for staff review, not load-bearing */ }
        batch = importBatchRepo.save(batch);

        auditService.log(org, importedBy, "LEGACY_LOANS_IMPORTED", "IMPORT_BATCH", batch.getId().toString(),
            "Imported " + success + "/" + results.size() + " rows from \"" + filename + "\"" +
                (failed > 0 ? " (" + failed + " row(s) failed — see batch detail)" : ""));

        return batch;
    }

    /** A ready-to-fill CSV template — headers plus one worked example row — so clients don't
     *  have to guess the exact column names/format from documentation alone. */
    public String buildCsvTemplate() {
        return String.join(",",
            "national_id","first_name","last_name","email","phone","gender","marital_status",
            "loan_type","amount","interest_rate","interest_rate_type","duration_months",
            "start_date","status","total_paid","outstanding_balance","currency","loan_reference","notes")
            + "\n" +
            String.join(",",
                "1198000000000000","Jean","Uwimana","", "0788000000","Male","Married",
                "PERSONAL","500000","10","MONTHLY","6",
                "2025-01-15","ACTIVE","150000","","RWF","OLD-LEDGER-0042","Migrated from paper ledger");
    }
}