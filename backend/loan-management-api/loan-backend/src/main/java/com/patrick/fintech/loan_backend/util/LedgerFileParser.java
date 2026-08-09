package com.patrick.fintech.loan_backend.util;

import org.apache.poi.ss.usermodel.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parses an uploaded borrower/loan ledger (CSV or XLSX) into a list of normalized row maps
 * (header -> cell value, both trimmed). Header matching is forgiving — "National ID",
 * "national_id", and "NationalID" all normalize to the same key — because this is reading
 * files real people export from Excel, not a fixed machine-generated format.
 */
public final class LedgerFileParser {

    private LedgerFileParser() {}

    public static List<Map<String, String>> parse(String filename, InputStream in) throws IOException {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) return parseCsv(in);
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) return parseExcel(in);
        throw new IllegalArgumentException("Unsupported file type — please upload a .csv or .xlsx file.");
    }

    public static String normalizeHeader(String header) {
        if (header == null) return "";
        return header.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s\\-]+", "_");
    }

    // ---------- CSV ----------

    private static List<Map<String, String>> parseCsv(InputStream in) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return rows;
            List<String> headers = splitCsvLine(headerLine).stream()
                .map(LedgerFileParser::normalizeHeader).toList();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> cells = splitCsvLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < cells.size() ? cells.get(i).trim() : "");
                }
                rows.add(row);
            }
        }
        return rows;
    }

    /** Minimal RFC4180-ish CSV line splitter — handles quoted fields containing commas/quotes,
     *  which a plain String.split(",") would silently corrupt. */
    private static List<String> splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else if (c == '"') inQuotes = false;
                else cur.append(c);
            } else {
                if (c == '"') inQuotes = true;
                else if (c == ',') { out.add(cur.toString()); cur.setLength(0); }
                else cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    // ---------- XLSX/XLS ----------

    private static List<Map<String, String>> parseExcel(InputStream in) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Iterator<Row> it = sheet.iterator();
            if (!it.hasNext()) return rows;

            Row headerRow = it.next();
            List<String> headers = new ArrayList<>();
            for (Cell c : headerRow) headers.add(normalizeHeader(formatter.formatCellValue(c)));

            while (it.hasNext()) {
                Row r = it.next();
                boolean allBlank = true;
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = r.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String val = cell == null ? "" : formatter.formatCellValue(cell).trim();
                    if (!val.isBlank()) allBlank = false;
                    row.put(headers.get(i), val);
                }
                if (!allBlank) rows.add(row);
            }
        }
        return rows;
    }
}
