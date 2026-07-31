package com.patrick.fintech.loan_backend.service;

// Explicit, non-conflicting OpenPDF Imports for PDF generation
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

// Explicit, non-conflicting Apache POI Imports for Excel generation
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * Shared Excel/PDF export logic for financial reports, so each report
 * controller doesn't reimplement cell styling and column layout.
 * Takes a simple "rows of named columns" shape (a List of ordered
 * LinkedHashMaps) so any report -- trial balance, balance sheet, P&L,
 * whatever comes next -- can reuse this without a report-specific
 * export method.
 */
@Service
public class ReportExportService {

    public byte[] toExcel(String title, List<String> columns, List<Map<String, Object>> rows) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(title);

            CellStyle headerStyle = wb.createCellStyle();
            
            // 🔑 FIX: Explicitly namespace the POI Font implementation to resolve ambiguity
            org.apache.poi.ss.usermodel.Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns.get(i));
                cell.setCellStyle(headerStyle);
            }

            int r = 1;
            for (Map<String, Object> rowData : rows) {
                Row row = sheet.createRow(r++);
                int c = 0;
                for (String col : columns) {
                    Object v = rowData.get(col);
                    Cell cell = row.createCell(c++);
                    if (v instanceof Number n) cell.setCellValue(n.doubleValue());
                    else cell.setCellValue(v != null ? v.toString() : "");
                }
            }

            for (int i = 0; i < columns.size(); i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Excel export: " + e.getMessage(), e);
        }
    }

    public byte[] toPdf(String title, List<String> columns, List<Map<String, Object>> rows, String orgName) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 54, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();

            // 🔑 FIX: Explicitly namespace OpenPDF Fonts to decouple from POI mappings
            com.lowagie.text.Font titleFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 16, com.lowagie.text.Font.BOLD);
            com.lowagie.text.Font subFont   = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 10, com.lowagie.text.Font.NORMAL, Color.GRAY);
            com.lowagie.text.Font headFont  = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 10, com.lowagie.text.Font.BOLD, Color.WHITE);
            com.lowagie.text.Font bodyFont  = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.NORMAL);

            doc.add(new Paragraph(orgName != null ? orgName : "", subFont));
            doc.add(new Paragraph(title, titleFont));
            doc.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(columns.size());
            table.setWidthPercentage(100);

            for (String col : columns) {
                PdfPCell cell = new PdfPCell(new Phrase(col, headFont));
                cell.setBackgroundColor(new Color(30, 41, 59)); // slate-800, matches admin UI
                cell.setPadding(6);
                table.addCell(cell);
            }

            for (Map<String, Object> rowData : rows) {
                for (String col : columns) {
                    Object v = rowData.get(col);
                    PdfPCell cell = new PdfPCell(new Phrase(formatCell(v), bodyFont));
                    cell.setPadding(5);
                    table.addCell(cell);
                }
            }

            doc.add(table);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF export: " + e.getMessage(), e);
        }
    }

    // Money and other decimal figures come in as raw doubles, which print with
    // full floating-point precision (e.g. 818987.40397372...) if left to
    // Object.toString() — unreadable, and prone to ugly mid-number line wraps
    // in a narrow PDF cell. Whole numbers (counts, installment numbers) are
    // left as-is; only fractional values get comma + 2-decimal formatting.
    private String formatCell(Object v) {
        if (v == null) return "";
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            return new java.text.DecimalFormat("#,##0.00").format(d);
        }
        if (v instanceof Integer || v instanceof Long) {
            return new java.text.DecimalFormat("#,##0").format(((Number) v).longValue());
        }
        return v.toString();
    }
}