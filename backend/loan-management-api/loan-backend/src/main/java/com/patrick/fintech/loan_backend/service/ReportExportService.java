package com.patrick.fintech.loan_backend.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.stereotype.Service;

import java.awt.Color;

import java.io.ByteArrayOutputStream;

import java.math.BigDecimal;

import java.text.DecimalFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import java.time.format.DateTimeFormatter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Shared export service for Excel and PDF reports.
 *
 * This service is intentionally generic so it can be reused by:
 *
 * - BNR reports
 * - CRB reports
 * - Accounting reports
 * - Loan reports
 * - Borrower reports
 * - Payment reports
 * - Regulatory reports
 * - Other reporting modules
 */
@Service
public class ReportExportService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String DEFAULT_REPORT_TITLE =
            "Report";


    // ============================================================
    // EXCEL
    // ============================================================

    /**
     * Generate an Excel XLSX report.
     *
     * @param title   report title
     * @param columns column names
     * @param rows    report data
     * @return XLSX file as byte array
     */
    public byte[] toExcel(
            String title,
            List<String> columns,
            List<Map<String, Object>> rows
    ) {

        String reportTitle =
                normalizeTitle(title);

        List<String> safeColumns =
                columns == null
                        ? Collections.emptyList()
                        : columns;

        List<Map<String, Object>> safeRows =
                rows == null
                        ? Collections.emptyList()
                        : rows;


        try (
                Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {

            // ====================================================
            // SHEET NAME
            // ====================================================

            String sheetName =
                    createSafeExcelSheetName(
                            reportTitle
                    );

            Sheet sheet =
                    workbook.createSheet(
                            sheetName
                    );


            // ====================================================
            // STYLES
            // ====================================================

            CellStyle titleStyle =
                    createExcelTitleStyle(
                            workbook
                    );

            CellStyle headerStyle =
                    createExcelHeaderStyle(
                            workbook
                    );

            CellStyle bodyStyle =
                    createExcelBodyStyle(
                            workbook
                    );

            CellStyle numberStyle =
                    createExcelNumberStyle(
                            workbook,
                            bodyStyle
                    );

            CellStyle integerStyle =
                    createExcelIntegerStyle(
                            workbook,
                            bodyStyle
                    );

            CellStyle dateStyle =
                    createExcelDateStyle(
                            workbook,
                            bodyStyle
                    );


            // ====================================================
            // TITLE
            // ====================================================

            Row titleRow =
                    sheet.createRow(0);

            titleRow.setHeightInPoints(24);

            Cell titleCell =
                    titleRow.createCell(0);

            titleCell.setCellValue(
                    reportTitle
            );

            titleCell.setCellStyle(
                    titleStyle
            );


            // ====================================================
            // HEADER
            // ====================================================

            Row headerRow =
                    sheet.createRow(2);

            headerRow.setHeightInPoints(35);

            for (
                    int index = 0;
                    index < safeColumns.size();
                    index++
            ) {

                Cell cell =
                        headerRow.createCell(index);

                String column =
                        safeColumns.get(index);

                cell.setCellValue(
                        column == null
                                ? ""
                                : column
                );

                cell.setCellStyle(
                        headerStyle
                );
            }


            // ====================================================
            // DATA
            // ====================================================

            int rowNumber = 3;

            for (
                    Map<String, Object> rowData :
                            safeRows
            ) {

                if (rowData == null) {
                    rowData =
                            Collections.emptyMap();
                }

                Row row =
                        sheet.createRow(
                                rowNumber++
                        );

                row.setHeightInPoints(24);


                for (
                        int columnIndex = 0;
                        columnIndex < safeColumns.size();
                        columnIndex++
                ) {

                    String column =
                            safeColumns.get(
                                    columnIndex
                            );

                    Object value =
                            rowData.get(
                                    column
                            );

                    Cell cell =
                            row.createCell(
                                    columnIndex
                            );

                    writeExcelValue(
                            cell,
                            value,
                            bodyStyle,
                            numberStyle,
                            integerStyle,
                            dateStyle
                    );
                }
            }


            // ====================================================
            // FREEZE HEADER
            // ====================================================

            sheet.createFreezePane(
                    0,
                    3
            );


            // ====================================================
            // FILTER
            // ====================================================

            if (!safeColumns.isEmpty()) {

                int lastDataRow =
                        Math.max(
                                2,
                                rowNumber - 1
                        );

                sheet.setAutoFilter(
                        new org.apache.poi.ss.util.CellRangeAddress(
                                2,
                                lastDataRow,
                                0,
                                safeColumns.size() - 1
                        )
                );
            }


            // ====================================================
            // COLUMN WIDTHS
            // ====================================================

            for (
                    int i = 0;
                    i < safeColumns.size();
                    i++
            ) {

                sheet.autoSizeColumn(i);

                int currentWidth =
                        sheet.getColumnWidth(i);

                int minimumWidth =
                        3000;

                int maximumWidth =
                        12000;

                int width =
                        Math.max(
                                minimumWidth,
                                Math.min(
                                        maximumWidth,
                                        currentWidth + 500
                                )
                        );

                sheet.setColumnWidth(
                        i,
                        width
                );
            }


            // ====================================================
            // SPECIFIC COLUMN WIDTHS
            // ====================================================

            for (
                    int i = 0;
                    i < safeColumns.size();
                    i++
            ) {

                String column =
                        safeColumns.get(i);

                if (column == null) {
                    continue;
                }


                if (
                        "Full Name".equals(column)
                                ||
                        "National ID".equals(column)
                                ||
                        "Loan Number".equals(column)
                                ||
                        "Repayment Classification".equals(column)
                ) {

                    sheet.setColumnWidth(
                            i,
                            6500
                    );
                }


                if (
                        "Loan Amount".equals(column)
                                ||
                        "Outstanding Balance".equals(column)
                ) {

                    sheet.setColumnWidth(
                            i,
                            5000
                    );
                }


                if (
                        "Borrower ID".equals(column)
                                ||
                        "Days Past Due".equals(column)
                                ||
                        "Credit Score".equals(column)
                ) {

                    sheet.setColumnWidth(
                            i,
                            4000
                    );
                }


                if (
                        "Phone".equals(column)
                                ||
                        "Branch".equals(column)
                ) {

                    sheet.setColumnWidth(
                            i,
                            5000
                    );
                }
            }


            // ====================================================
            // WRITE WORKBOOK
            // ====================================================

            workbook.write(
                    output
            );

            return output.toByteArray();

        } catch (Exception exception) {

            throw new RuntimeException(
                    "Failed to generate Excel export",
                    exception
            );
        }
    }


    // ============================================================
    // EXCEL TITLE STYLE
    // ============================================================

    private CellStyle createExcelTitleStyle(
            Workbook workbook
    ) {

        CellStyle style =
                workbook.createCellStyle();

        org.apache.poi.ss.usermodel.Font font =
                workbook.createFont();

        font.setBold(true);

        font.setFontHeightInPoints(
                (short) 16
        );

        style.setFont(font);

        style.setAlignment(
                HorizontalAlignment.LEFT
        );

        return style;
    }


    // ============================================================
    // EXCEL HEADER STYLE
    // ============================================================

    private CellStyle createExcelHeaderStyle(
            Workbook workbook
    ) {

        CellStyle style =
                workbook.createCellStyle();

        org.apache.poi.ss.usermodel.Font font =
                workbook.createFont();

        font.setBold(true);

        font.setColor(
                IndexedColors.WHITE.getIndex()
        );

        font.setFontHeightInPoints(
                (short) 10
        );

        style.setFont(font);

        style.setFillForegroundColor(
                IndexedColors.DARK_BLUE.getIndex()
        );

        style.setFillPattern(
                FillPatternType.SOLID_FOREGROUND
        );

        style.setAlignment(
                HorizontalAlignment.CENTER
        );

        style.setVerticalAlignment(
                VerticalAlignment.CENTER
        );

        style.setWrapText(true);

        style.setBorderBottom(
                BorderStyle.THIN
        );

        return style;
    }


    // ============================================================
    // EXCEL BODY STYLE
    // ============================================================

    private CellStyle createExcelBodyStyle(
            Workbook workbook
    ) {

        CellStyle style =
                workbook.createCellStyle();

        style.setVerticalAlignment(
                VerticalAlignment.CENTER
        );

        style.setWrapText(true);

        return style;
    }


    // ============================================================
    // EXCEL NUMBER STYLE
    // ============================================================

    private CellStyle createExcelNumberStyle(
            Workbook workbook,
            CellStyle bodyStyle
    ) {

        CellStyle style =
                workbook.createCellStyle();

        style.cloneStyleFrom(
                bodyStyle
        );

        style.setAlignment(
                HorizontalAlignment.RIGHT
        );

        DataFormat dataFormat =
                workbook.createDataFormat();

        style.setDataFormat(
                dataFormat.getFormat(
                        "#,##0.00"
                )
        );

        return style;
    }


    // ============================================================
    // EXCEL INTEGER STYLE
    // ============================================================

    private CellStyle createExcelIntegerStyle(
            Workbook workbook,
            CellStyle bodyStyle
    ) {

        CellStyle style =
                workbook.createCellStyle();

        style.cloneStyleFrom(
                bodyStyle
        );

        style.setAlignment(
                HorizontalAlignment.RIGHT
        );

        DataFormat dataFormat =
                workbook.createDataFormat();

        style.setDataFormat(
                dataFormat.getFormat(
                        "#,##0"
                )
        );

        return style;
    }


    // ============================================================
    // EXCEL DATE STYLE
    // ============================================================

    private CellStyle createExcelDateStyle(
            Workbook workbook,
            CellStyle bodyStyle
    ) {

        CellStyle style =
                workbook.createCellStyle();

        style.cloneStyleFrom(
                bodyStyle
        );

        DataFormat dataFormat =
                workbook.createDataFormat();

        style.setDataFormat(
                dataFormat.getFormat(
                        "yyyy-mm-dd"
                )
        );

        return style;
    }


    // ============================================================
    // EXCEL VALUE WRITER
    // ============================================================

    private void writeExcelValue(
            Cell cell,
            Object value,
            CellStyle bodyStyle,
            CellStyle numberStyle,
            CellStyle integerStyle,
            CellStyle dateStyle
    ) {

        if (value == null) {

            cell.setCellValue("");

            cell.setCellStyle(
                    bodyStyle
            );

            return;
        }


        // ========================================================
        // LOCAL DATE
        // ========================================================

        if (value instanceof LocalDate date) {

            cell.setCellValue(
                    date.format(
                            DATE_FORMAT
                    )
            );

            cell.setCellStyle(
                    dateStyle
            );

            return;
        }


        // ========================================================
        // LOCAL DATE TIME
        // ========================================================

        if (
                value instanceof LocalDateTime dateTime
        ) {

            cell.setCellValue(
                    dateTime.format(
                            DATE_TIME_FORMAT
                    )
            );

            cell.setCellStyle(
                    bodyStyle
            );

            return;
        }


        // ========================================================
        // INTEGER TYPES
        // ========================================================

        if (
                value instanceof Byte
                        ||
                value instanceof Short
                        ||
                value instanceof Integer
                        ||
                value instanceof Long
        ) {

            Number number =
                    (Number) value;

            cell.setCellValue(
                    number.doubleValue()
            );

            cell.setCellStyle(
                    integerStyle
            );

            return;
        }


        // ========================================================
        // BIG INTEGER
        // ========================================================

        if (
                value instanceof java.math.BigInteger
        ) {

            cell.setCellValue(
                    value.toString()
            );

            cell.setCellStyle(
                    bodyStyle
            );

            return;
        }


        // ========================================================
        // DECIMAL / DOUBLE / FLOAT
        // ========================================================

        if (
                value instanceof BigDecimal
                        ||
                value instanceof Double
                        ||
                value instanceof Float
        ) {

            Number number =
                    (Number) value;

            cell.setCellValue(
                    number.doubleValue()
            );

            cell.setCellStyle(
                    numberStyle
            );

            return;
        }


        // ========================================================
        // OTHER NUMBER TYPES
        // ========================================================

        if (value instanceof Number number) {

            cell.setCellValue(
                    number.doubleValue()
            );

            cell.setCellStyle(
                    numberStyle
            );

            return;
        }


        // ========================================================
        // STRING / BOOLEAN / ENUM / OTHER
        // ========================================================

        cell.setCellValue(
                value.toString()
        );

        cell.setCellStyle(
                bodyStyle
        );
    }


    // ============================================================
    // PDF
    // ============================================================

    /**
     * Generate a PDF report.
     *
     * @param title            report title
     * @param columns          column names
     * @param rows             report rows
     * @param organizationName organization / tenant name
     * @return PDF file as byte array
     */
    public byte[] toPdf(
            String title,
            List<String> columns,
            List<Map<String, Object>> rows,
            String organizationName
    ) {

        String reportTitle =
                normalizeTitle(title);

        String safeOrganizationName =
                organizationName == null
                        ? ""
                        : organizationName;

        List<String> safeColumns =
                columns == null
                        ? Collections.emptyList()
                        : columns;

        List<Map<String, Object>> safeRows =
                rows == null
                        ? Collections.emptyList()
                        : rows;


        // ========================================================
        // VALIDATE COLUMNS
        // ========================================================

        if (safeColumns.isEmpty()) {

            throw new IllegalArgumentException(
                    "Cannot generate PDF report without columns"
            );
        }


        try (
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {

            // ====================================================
            // PAGE SIZE
            // ====================================================

            /*
             * CRB and regulatory reports normally contain many
             * columns. Landscape A4 gives much more horizontal
             * space than portrait.
             */
            Rectangle pageSize =
                    PageSize.A4.rotate();


            Document document =
                    new Document(
                            pageSize,
                            24,
                            24,
                            42,
                            32
                    );


            PdfWriter writer =
                    PdfWriter.getInstance(
                            document,
                            output
                    );


            writer.setPageEvent(
                    new ReportPageEvent(
                            reportTitle
                    )
            );


            document.open();


            // ====================================================
            // FONTS
            // ====================================================

            com.lowagie.text.Font organizationFont =
                    new com.lowagie.text.Font(
                            com.lowagie.text.Font.HELVETICA,
                            9,
                            com.lowagie.text.Font.NORMAL,
                            Color.DARK_GRAY
                    );


            com.lowagie.text.Font titleFont =
                    new com.lowagie.text.Font(
                            com.lowagie.text.Font.HELVETICA,
                            15,
                            com.lowagie.text.Font.BOLD,
                            new Color(
                                    15,
                                    23,
                                    42
                            )
                    );


            com.lowagie.text.Font metadataFont =
                    new com.lowagie.text.Font(
                            com.lowagie.text.Font.HELVETICA,
                            8,
                            com.lowagie.text.Font.NORMAL,
                            Color.DARK_GRAY
                    );


            com.lowagie.text.Font headerFont =
                    new com.lowagie.text.Font(
                            com.lowagie.text.Font.HELVETICA,
                            7,
                            com.lowagie.text.Font.BOLD,
                            Color.WHITE
                    );


            com.lowagie.text.Font bodyFont =
                    new com.lowagie.text.Font(
                            com.lowagie.text.Font.HELVETICA,
                            6.8f,
                            com.lowagie.text.Font.NORMAL,
                            Color.BLACK
                    );


            // ====================================================
            // ORGANIZATION
            // ====================================================

            if (!safeOrganizationName.isBlank()) {

                Paragraph organizationParagraph =
                        new Paragraph(
                                safeOrganizationName,
                                organizationFont
                        );

                organizationParagraph.setSpacingAfter(
                        3
                );

                document.add(
                        organizationParagraph
                );
            }


            // ====================================================
            // TITLE
            // ====================================================

            Paragraph titleParagraph =
                    new Paragraph(
                            reportTitle,
                            titleFont
                    );

            titleParagraph.setSpacingAfter(
                    4
            );

            document.add(
                    titleParagraph
            );


            // ====================================================
            // GENERATED INFORMATION
            // ====================================================

            String generatedAt =
                    LocalDateTime.now()
                            .format(
                                    DATE_TIME_FORMAT
                            );

            Paragraph generatedParagraph =
                    new Paragraph(
                            "Generated: "
                                    + generatedAt
                                    + "    |    Records: "
                                    + safeRows.size(),
                            metadataFont
                    );

            generatedParagraph.setSpacingAfter(
                    10
            );

            document.add(
                    generatedParagraph
            );


            // ====================================================
            // TABLE
            // ====================================================

            PdfPTable table =
                    new PdfPTable(
                            safeColumns.size()
                    );

            table.setWidthPercentage(
                    100
            );

            table.setHeaderRows(
                    1
            );


            // ====================================================
            // COLUMN WIDTHS
            // ====================================================

            float[] widths =
                    buildPdfColumnWidths(
                            safeColumns
                    );


            if (
                    widths.length ==
                            safeColumns.size()
            ) {

                table.setWidths(
                        widths
                );
            }


            // ====================================================
            // HEADER
            // ====================================================

            for (
                    String column :
                            safeColumns
            ) {

                String safeColumn =
                        column == null
                                ? ""
                                : column;

                PdfPCell cell =
                        new PdfPCell(
                                new Phrase(
                                        safeColumn,
                                        headerFont
                                )
                        );

                cell.setBackgroundColor(
                        new Color(
                                30,
                                41,
                                59
                        )
                );

                cell.setHorizontalAlignment(
                        Element.ALIGN_CENTER
                );

                cell.setVerticalAlignment(
                        Element.ALIGN_MIDDLE
                );

                cell.setPadding(
                        4
                );

                cell.setLeading(
                        8,
                        0
                );

                table.addCell(
                        cell
                );
            }


            // ====================================================
            // BODY
            // ====================================================

            boolean alternate =
                    false;


            for (
                    Map<String, Object> originalRowData :
                            safeRows
            ) {

                Map<String, Object> rowData =
                        originalRowData == null
                                ? Collections.emptyMap()
                                : originalRowData;


                alternate =
                        !alternate;


                for (
                        String column :
                                safeColumns
                ) {

                    Object value =
                            rowData.get(
                                    column
                            );


                    PdfPCell cell =
                            new PdfPCell(
                                    new Phrase(
                                            formatPdfCell(
                                                    value
                                            ),
                                            bodyFont
                                    )
                            );


                    cell.setPadding(
                            3.5f
                    );

                    cell.setLeading(
                            8,
                            0
                    );

                    cell.setVerticalAlignment(
                            Element.ALIGN_MIDDLE
                    );


                    if (
                            isNumeric(
                                    value
                            )
                    ) {

                        cell.setHorizontalAlignment(
                                Element.ALIGN_RIGHT
                        );

                    } else {

                        cell.setHorizontalAlignment(
                                Element.ALIGN_LEFT
                        );
                    }


                    if (alternate) {

                        cell.setBackgroundColor(
                                new Color(
                                        248,
                                        250,
                                        252
                                )
                        );
                    }


                    table.addCell(
                            cell
                    );
                }
            }


            // ====================================================
            // ADD TABLE
            // ====================================================

            document.add(
                    table
            );


            // ====================================================
            // CLOSE
            // ====================================================

            document.close();


            return output.toByteArray();

        } catch (Exception exception) {

            throw new RuntimeException(
                    "Failed to generate PDF export",
                    exception
            );
        }
    }


    // ============================================================
    // PDF COLUMN WIDTHS
    // ============================================================

    private float[] buildPdfColumnWidths(
            List<String> columns
    ) {

        if (columns == null || columns.isEmpty()) {

            return new float[0];
        }


        float[] widths =
                new float[columns.size()];


        for (
                int i = 0;
                i < columns.size();
                i++
        ) {

            String column =
                    columns.get(i);


            if (column == null) {

                widths[i] =
                        1.0f;

                continue;
            }


            widths[i] =
                    switch (column) {

                        case "Borrower ID" ->
                                0.9f;

                        case "Full Name" ->
                                1.8f;

                        case "National ID" ->
                                1.6f;

                        case "Date of Birth" ->
                                1.15f;

                        case "Gender" ->
                                0.8f;

                        case "Phone" ->
                                1.25f;

                        case "Loan Number" ->
                                1.65f;

                        case "Loan Type" ->
                                1.15f;

                        case "Loan Status" ->
                                1.05f;

                        case "Repayment Classification" ->
                                1.65f;

                        case "Loan Amount" ->
                                1.35f;

                        case "Outstanding Balance" ->
                                1.45f;

                        case "Days Past Due" ->
                                0.85f;

                        case "Credit Score" ->
                                0.9f;

                        case "Date Opened" ->
                                1.1f;

                        case "Last Payment" ->
                                1.1f;

                        case "Maturity Date" ->
                                1.1f;

                        case "Date Closed" ->
                                1.1f;

                        case "Branch" ->
                                1.25f;

                        case "Currency" ->
                                0.75f;

                        default ->
                                1.0f;
                    };
        }


        return widths;
    }


    // ============================================================
    // NUMERIC CHECK
    // ============================================================

    private boolean isNumeric(
            Object value
    ) {

        return value instanceof Number;
    }


    // ============================================================
    // PDF CELL FORMAT
    // ============================================================

    private String formatPdfCell(
            Object value
    ) {

        if (value == null) {

            return "";
        }


        // ========================================================
        // LOCAL DATE
        // ========================================================

        if (value instanceof LocalDate date) {

            return date.format(
                    DATE_FORMAT
            );
        }


        // ========================================================
        // LOCAL DATE TIME
        // ========================================================

        if (
                value instanceof LocalDateTime dateTime
        ) {

            return dateTime.format(
                    DATE_TIME_FORMAT
            );
        }


        // ========================================================
        // BIG DECIMAL
        // ========================================================

        if (value instanceof BigDecimal decimal) {

            return formatMoney(
                    decimal
            );
        }


        // ========================================================
        // FLOAT / DOUBLE
        // ========================================================

        if (
                value instanceof Double
                        ||
                value instanceof Float
        ) {

            return formatMoney(
                    BigDecimal.valueOf(
                            ((Number) value)
                                    .doubleValue()
                    )
            );
        }


        // ========================================================
        // INTEGER TYPES
        // ========================================================

        if (
                value instanceof Integer
                        ||
                value instanceof Long
                        ||
                value instanceof Short
                        ||
                value instanceof Byte
        ) {

            return formatInteger(
                    ((Number) value)
                            .longValue()
            );
        }


        // ========================================================
        // OTHER NUMBER
        // ========================================================

        if (value instanceof Number number) {

            return formatMoney(
                    BigDecimal.valueOf(
                            number.doubleValue()
                    )
            );
        }


        // ========================================================
        // STRING / ENUM / BOOLEAN / OTHER
        // ========================================================

        return value.toString();
    }


    // ============================================================
    // MONEY FORMAT
    // ============================================================

    private String formatMoney(
            BigDecimal value
    ) {

        if (value == null) {

            return "";
        }


        DecimalFormat formatter =
                new DecimalFormat(
                        "#,##0.00"
                );

        return formatter.format(
                value
        );
    }


    // ============================================================
    // INTEGER FORMAT
    // ============================================================

    private String formatInteger(
            long value
    ) {

        DecimalFormat formatter =
                new DecimalFormat(
                        "#,##0"
                );

        return formatter.format(
                value
        );
    }


    // ============================================================
    // NORMALIZE TITLE
    // ============================================================

    private String normalizeTitle(
            String title
    ) {

        if (
                title == null
                        ||
                title.isBlank()
        ) {

            return DEFAULT_REPORT_TITLE;
        }


        return title.trim();
    }


    // ============================================================
    // SAFE EXCEL SHEET NAME
    // ============================================================

    private String createSafeExcelSheetName(
            String title
    ) {

        String safeName =
                title == null
                        ? DEFAULT_REPORT_TITLE
                        : title.trim();


        if (safeName.isBlank()) {

            safeName =
                    DEFAULT_REPORT_TITLE;
        }


        /*
         * Excel does not allow:
         *
         * :
         * \
         * /
         * ?
         * *
         * [
         * ]
         */
        safeName =
                safeName.replace(
                        ':',
                        '_'
                );

        safeName =
                safeName.replace(
                        '\\',
                        '_'
                );

        safeName =
                safeName.replace(
                        '/',
                        '_'
                );

        safeName =
                safeName.replace(
                        '?',
                        '_'
                );

        safeName =
                safeName.replace(
                        '*',
                        '_'
                );

        safeName =
                safeName.replace(
                        '[',
                        '_'
                );

        safeName =
                safeName.replace(
                        ']',
                        '_'
                );


        if (safeName.length() > 31) {

            safeName =
                    safeName.substring(
                            0,
                            31
                    );
        }


        if (safeName.isBlank()) {

            return DEFAULT_REPORT_TITLE;
        }


        return safeName;
    }


    // ============================================================
    // PDF PAGE EVENT
    // ============================================================

    private static class ReportPageEvent
            extends PdfPageEventHelper {

        private final String title;


        private final com.lowagie.text.Font footerFont =
                new com.lowagie.text.Font(
                        com.lowagie.text.Font.HELVETICA,
                        7,
                        com.lowagie.text.Font.NORMAL,
                        Color.GRAY
                );


        ReportPageEvent(
                String title
        ) {

            this.title =
                    title == null
                            || title.isBlank()
                            ? DEFAULT_REPORT_TITLE
                            : title;
        }


        @Override
        public void onEndPage(
                PdfWriter writer,
                Document document
        ) {

            String footer =
                    title
                            + "    |    Page "
                            + writer.getPageNumber();


            Phrase phrase =
                    new Phrase(
                            footer,
                            footerFont
                    );


            float centerX =
                    (
                            document.left()
                                    +
                            document.right()
                    ) / 2;


            ColumnText.showTextAligned(
                    writer.getDirectContent(),
                    Element.ALIGN_CENTER,
                    phrase,
                    centerX,
                    18,
                    0
            );
        }
    }
}