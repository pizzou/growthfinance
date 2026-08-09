package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayFinancialPrecisionMigrationTest {

    @Test
    void v48DropsDependentViewsBeforeChangingFinancialColumnTypes() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V48__financial_precision_and_payment_idempotency.sql");
        String sql = Files.readString(migration);

        int dropPortfolio = sql.indexOf("DROP VIEW IF EXISTS v_portfolio_summary");
        int alterLoans = sql.indexOf("ALTER TABLE loans");
        int recreatePortfolio = sql.indexOf("CREATE OR REPLACE VIEW v_portfolio_summary");

        assertTrue(dropPortfolio >= 0, "v_portfolio_summary must be dropped before the type conversion");
        assertTrue(alterLoans > dropPortfolio, "loan type conversion must happen after dependent view removal");
        assertTrue(recreatePortfolio > alterLoans, "v_portfolio_summary must be recreated after the conversion");
        assertTrue(sql.contains("ALTER COLUMN match_score TYPE NUMERIC(19,9)"));
        assertTrue(sql.contains("ALTER COLUMN rate TYPE NUMERIC(19,12)"));
    }
}
