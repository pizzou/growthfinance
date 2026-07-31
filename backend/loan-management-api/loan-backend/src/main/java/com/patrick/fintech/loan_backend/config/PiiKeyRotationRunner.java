package com.patrick.fintech.loan_backend.config;

import com.patrick.fintech.loan_backend.security.HmacIndexer;
import com.patrick.fintech.loan_backend.security.PiiCrypto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

/**
 * One-off maintenance tool that re-encrypts every PII column under the current
 * APP_ENCRYPTION_KEY / APP_INDEX_KEY, and re-derives the HMAC blind-index columns
 * (phone_hash, national_id_hash) to match. This is the tool referenced by
 * PiiCrypto's javadoc and SECRETS_AND_KEY_ROTATION.md — before this class existed,
 * there was no way to actually finish rotating a compromised encryption key.
 *
 * <h2>Does nothing on a normal boot</h2>
 * This only runs when explicitly requested via the {@code ROTATE_PII_KEYS} environment
 * variable — never as a side effect of starting the app normally. Two modes:
 * <ul>
 *   <li>{@code ROTATE_PII_KEYS=dry-run} — reads every row, reports how many would change
 *       and how many are already on the current key version, writes nothing.</li>
 *   <li>{@code ROTATE_PII_KEYS=apply} — does the same, but actually re-encrypts and commits,
 *       in batches of {@link #BATCH_SIZE}, so a crash partway through loses no more than the
 *       in-flight batch.</li>
 * </ul>
 * Either way, the process exits after the run instead of continuing on to serve traffic —
 * this is a maintenance job, not something that should share a process with the API server.
 *
 * <h2>Idempotent and resumable</h2>
 * Re-running this (in either mode) is always safe. A row already on the current key
 * decrypts-then-re-encrypts to a different ciphertext (fresh random IV) but the same
 * plaintext, so nothing observable changes. If a run is interrupted, just run it again.
 *
 * <h2>Before running this against real data</h2>
 * Run it against a restored copy of the production database first, with
 * {@code ROTATE_PII_KEYS=dry-run}, then {@code apply}, and verify the app reads correctly
 * against the result — before ever pointing it at the live database. See
 * DISASTER_RECOVERY.md for how to restore a copy to test against.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PiiKeyRotationRunner implements CommandLineRunner {

    private static final int BATCH_SIZE = 200;

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager txManager;

    @Override
    public void run(String... args) {
        String mode = System.getenv("ROTATE_PII_KEYS");
        if (mode == null || mode.isBlank()) {
            return; // not requested — normal boot, do nothing
        }
        boolean dryRun = switch (mode.trim().toLowerCase()) {
            case "dry-run" -> true;
            case "apply" -> false;
            default -> throw new IllegalStateException(
                "ROTATE_PII_KEYS must be 'dry-run' or 'apply', got: '" + mode + "'");
        };

        if (!PiiCrypto.rotationInProgress()) {
            log.error("ROTATE_PII_KEYS={} was set, but APP_ENCRYPTION_KEY_PREVIOUS is not — there is no old key to " +
                "migrate away from. Set APP_ENCRYPTION_KEY_PREVIOUS/APP_ENCRYPTION_KEY_PREVIOUS_VERSION to the key " +
                "you are rotating away from before running this. Aborting; no changes made.", mode);
            exit(1);
            return;
        }

        log.warn("========================================================================");
        log.warn(" PII key rotation — mode: {}", dryRun ? "DRY RUN (no writes)" : "APPLY (will write)");
        log.warn(" Target current key version: {}", PiiCrypto.currentVersionTag());
        log.warn("========================================================================");

        Summary borrowers = migrateBorrowers(dryRun);
        Summary guarantors = migrateGuarantors(dryRun);

        log.warn("------------------------------------------------------------------------");
        log.warn(" borrowers:  {} scanned, {} re-encrypted{}, {} already current, {} errors",
            borrowers.scanned, borrowers.changed, dryRun ? " (would be)" : "", borrowers.alreadyCurrent, borrowers.errors);
        log.warn(" guarantors: {} scanned, {} re-encrypted{}, {} already current, {} errors",
            guarantors.scanned, guarantors.changed, dryRun ? " (would be)" : "", guarantors.alreadyCurrent, guarantors.errors);
        if (borrowers.errors > 0 || guarantors.errors > 0) {
            log.error(" Completed WITH ERRORS — see above. Do not remove APP_ENCRYPTION_KEY_PREVIOUS/" +
                "APP_INDEX_KEY_PREVIOUS until a run completes with zero errors.");
        } else if (dryRun) {
            log.warn(" Dry run complete, nothing written. Re-run with ROTATE_PII_KEYS=apply to actually migrate.");
        } else {
            log.warn(" Apply complete. Once you're satisfied (spot-check a few records), remove " +
                "APP_ENCRYPTION_KEY_PREVIOUS, APP_ENCRYPTION_KEY_PREVIOUS_VERSION, and APP_INDEX_KEY_PREVIOUS " +
                "from the environment and restart — the old keys are no longer needed anywhere.");
        }
        log.warn("========================================================================");
        exit(borrowers.errors > 0 || guarantors.errors > 0 ? 1 : 0);
    }

    private void exit(int code) {
        System.exit(code);
    }

    private static final List<String> BORROWER_ENC_COLUMNS = List.of(
        "phone", "alternate_phone", "national_id", "passport_number", "tax_identification_number",
        "single_certificate_number", "spouse_national_id", "spouse_phone",
        "address", "address_line1", "address_line2", "bank_account_number");

    private static final List<String> GUARANTOR_ENC_COLUMNS = List.of("national_id", "phone", "address");

    private Summary migrateBorrowers(boolean dryRun) {
        Summary summary = new Summary();
        TransactionTemplate tx = new TransactionTemplate(txManager);
        long lastId = 0;
        while (true) {
            List<Map<String, Object>> batch = fetchBatch("borrowers",
                columnList(BORROWER_ENC_COLUMNS), lastId, BATCH_SIZE);
            if (batch.isEmpty()) break;

            tx.executeWithoutResult(status -> {
                for (Map<String, Object> row : batch) {
                    summary.scanned++;
                    try {
                        boolean changed = migrateBorrowerRow(row, dryRun);
                        if (changed) summary.changed++; else summary.alreadyCurrent++;
                    } catch (Exception e) {
                        summary.errors++;
                        log.error("Failed to migrate borrowers.id={}", row.get("id"), e);
                    }
                }
            });
            lastId = ((Number) batch.get(batch.size() - 1).get("id")).longValue();
            log.info("borrowers: processed through id={} ({} scanned so far)", lastId, summary.scanned);
        }
        return summary;
    }

    private boolean migrateBorrowerRow(Map<String, Object> row, boolean dryRun) {
        long id = ((Number) row.get("id")).longValue();
        Map<String, String> newValues = new java.util.HashMap<>();
        boolean anyChanged = false;
        for (String col : BORROWER_ENC_COLUMNS) {
            String stored = (String) row.get(col);
            String plaintext = PiiCrypto.decrypt(stored);
            String reEncrypted = PiiCrypto.encryptCurrent(plaintext);
            newValues.put(col, reEncrypted);
            // Ciphertext always differs on re-encryption (fresh random IV each time), even when
            // nothing meaningfully changed — so "changed" means "wasn't already tagged with the
            // current key version", not "output bytes differ".
            if (!alreadyCurrentVersion(stored)) anyChanged = true;
        }
        String newPhoneHash = HmacIndexer.index(PiiCrypto.decrypt((String) row.get("phone")));
        String newNationalIdHash = HmacIndexer.index(PiiCrypto.decrypt((String) row.get("national_id")));

        if (!dryRun) {
            jdbcTemplate.update(
                "UPDATE borrowers SET phone=?, alternate_phone=?, national_id=?, passport_number=?, " +
                "tax_identification_number=?, single_certificate_number=?, spouse_national_id=?, spouse_phone=?, " +
                "address=?, address_line1=?, address_line2=?, bank_account_number=?, phone_hash=?, national_id_hash=? " +
                "WHERE id=?",
                newValues.get("phone"), newValues.get("alternate_phone"), newValues.get("national_id"),
                newValues.get("passport_number"), newValues.get("tax_identification_number"),
                newValues.get("single_certificate_number"), newValues.get("spouse_national_id"),
                newValues.get("spouse_phone"), newValues.get("address"), newValues.get("address_line1"),
                newValues.get("address_line2"), newValues.get("bank_account_number"),
                newPhoneHash, newNationalIdHash, id);
        }
        return anyChanged;
    }

    private Summary migrateGuarantors(boolean dryRun) {
        Summary summary = new Summary();
        TransactionTemplate tx = new TransactionTemplate(txManager);
        long lastId = 0;
        while (true) {
            List<Map<String, Object>> batch = fetchBatch("guarantors",
                columnList(GUARANTOR_ENC_COLUMNS), lastId, BATCH_SIZE);
            if (batch.isEmpty()) break;

            tx.executeWithoutResult(status -> {
                for (Map<String, Object> row : batch) {
                    summary.scanned++;
                    try {
                        boolean changed = migrateGuarantorRow(row, dryRun);
                        if (changed) summary.changed++; else summary.alreadyCurrent++;
                    } catch (Exception e) {
                        summary.errors++;
                        log.error("Failed to migrate guarantors.id={}", row.get("id"), e);
                    }
                }
            });
            lastId = ((Number) batch.get(batch.size() - 1).get("id")).longValue();
            log.info("guarantors: processed through id={} ({} scanned so far)", lastId, summary.scanned);
        }
        return summary;
    }

    private boolean migrateGuarantorRow(Map<String, Object> row, boolean dryRun) {
        long id = ((Number) row.get("id")).longValue();
        boolean anyChanged = false;
        Map<String, String> newValues = new java.util.HashMap<>();
        for (String col : GUARANTOR_ENC_COLUMNS) {
            String stored = (String) row.get(col);
            String plaintext = PiiCrypto.decrypt(stored);
            String reEncrypted = PiiCrypto.encryptCurrent(plaintext);
            newValues.put(col, reEncrypted);
            if (!alreadyCurrentVersion(stored)) anyChanged = true;
        }
        if (!dryRun) {
            jdbcTemplate.update("UPDATE guarantors SET national_id=?, phone=?, address=? WHERE id=?",
                newValues.get("national_id"), newValues.get("phone"), newValues.get("address"), id);
        }
        return anyChanged;
    }

    private boolean alreadyCurrentVersion(String stored) {
        if (stored == null || stored.isBlank()) return true; // nothing to migrate
        if (!stored.startsWith("enc:")) return false; // plaintext row — needs first-time encryption
        return stored.startsWith("enc:" + PiiCrypto.currentVersionTag() + ":");
    }

    private List<Map<String, Object>> fetchBatch(String table, String columns, long afterId, int limit) {
        RowMapper<Map<String, Object>> mapper = (ResultSet rs, int rowNum) -> {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("id", rs.getLong("id"));
            for (String col : columns.split(",")) {
                map.put(col.trim(), rs.getString(col.trim()));
            }
            return map;
        };
        return jdbcTemplate.query(
            "SELECT id, " + columns + " FROM " + table + " WHERE id > ? ORDER BY id ASC LIMIT ?",
            mapper, afterId, limit);
    }

    private String columnList(List<String> cols) {
        return String.join(", ", cols);
    }

    private static class Summary {
        int scanned = 0;
        int changed = 0;
        int alreadyCurrent = 0;
        int errors = 0;
    }
}