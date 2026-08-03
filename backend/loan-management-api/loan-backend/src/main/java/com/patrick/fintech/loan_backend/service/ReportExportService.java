
package com.patrick.fintech.loan_backend.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;

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

import java.text.DecimalFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import java.time.format.DateTimeFormatter;

import java.util.List;
import java.util.Map;

/**
 * Shared export service for Excel, PDF and CSV-compatible report data.
 *
 * The service is intentionally generic so the same exporter can be reused
 * by BNR, CRB, accounting and other regulatory reports.
 */
@Service
public class ReportExportService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final DecimalFormat MONEY_FORMAT =
            new DecimalFormat("#,##0.00");

    private static final DecimalFormat INTEGER_FORMAT =
            new DecimalFormat("#,##0");


    // ============================================================
    // EXCEL
    // ============================================================

    public byte[] toExcel(
            String title,
            List<String> columns,
            List<Map<String, Object>> rows
    ) {

        try (
                Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {

            String safeSheetName =
                    title == null || title.isBlank()
                            ? "Report"
                            : title;

            if (safeSheetName.length() > 31) {
                safeSheetName =
                        safeSheetName.substring(0, 31);
            }

            Sheet sheet =
                    workbook.createSheet(
                            safeSheetName
                    );


            // ====================================================
            // STYLES
            // ====================================================

            CellStyle titleStyle =
                    workbook.createCellStyle();

            org.apache.poi.ss.usermodel.Font titleFont =
                    workbook.createFont();

            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);

            titleStyle.setFont(titleFont);

            titleStyle.setAlignment(
                    HorizontalAlignment.LEFT
            );


            CellStyle headerStyle =
                    workbook.createCellStyle();

            org.apache.poi.ss.usermodel.Font headerFont =
                    workbook.createFont();

            headerFont.setBold(true);
            headerFont.setColor(
                    IndexedColors.WHITE.getIndex()
            );
            headerFont.setFontHeightInPoints(
                    (short) 10
            );

            headerStyle.setFont(headerFont);

            headerStyle.setFillForegroundColor(
                    IndexedColors.DARK_BLUE.getIndex()
            );

            headerStyle.setFillPattern(
                    FillPatternType.SOLID_FOREGROUND
            );

            headerStyle.setAlignment(
                    HorizontalAlignment.CENTER
            );

            headerStyle.setVerticalAlignment(
                    VerticalAlignment.CENTER
            );

            headerStyle.setWrapText(true);

            headerStyle.setBorderBottom(
                    BorderStyle.THIN
            );


            CellStyle bodyStyle =
                    workbook.createCellStyle();

            bodyStyle.setVerticalAlignment(
                    VerticalAlignment.CENTER
            );

            bodyStyle.setWrapText(true);


            CellStyle numberStyle =
                    workbook.createCellStyle();

            numberStyle.cloneStyleFrom(
                    bodyStyle
            );

            numberStyle.setAlignment(
                    HorizontalAlignment.RIGHT
            );

            DataFormat dataFormat =
                    workbook.createDataFormat();

            numberStyle.setDataFormat(
                    dataFormat.getFormat(
                            "#,##0.00"
                    )
            );


            CellStyle integerStyle =
                    workbook.createCellStyle();

            integerStyle.cloneStyleFrom(
                    bodyStyle
            );

            integerStyle.setAlignment(
                    HorizontalAlignment.RIGHT
            );

            integerStyle.setDataFormat(
                    dataFormat.getFormat(
                            "#,##0"
                    )
            );


            CellStyle dateStyle =
                    workbook.createCellStyle();

            dateStyle.cloneStyleFrom(
                    bodyStyle
            );

            dateStyle.setDataFormat(
                    dataFormat.getFormat(
                            "yyyy-mm-dd"
                    )
            );


            // ====================================================
            // TITLE
            // ====================================================

            Row titleRow =
                    sheet.createRow(0);

            titleRow.setHeightInPoints(
                    24
            );

            Cell titleCell =
                    titleRow.createCell(0);

            titleCell.setCellValue(
                    title == null
                            ? "Report"
                            : title
            );

            titleCell.setCellStyle(
                    titleStyle
            );


            // ====================================================
            // HEADER
            // ====================================================

            Row headerRow =
                    sheet.createRow(2);

            headerRow.setHeightInPoints(
                    35
            );

            for (
                    int index = 0;
                    index < columns.size();
                    index++
            ) {

                Cell cell =
                        headerRow.createCell(index);

                cell.setCellValue(
                        columns.get(index)
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
                            rows
            ) {

                Row row =
                        sheet.createRow(
                                rowNumber++
                        );

                row.setHeightInPoints(
                        24
                );

                for (
                        int columnIndex = 0;
                        columnIndex < columns.size();
                        columnIndex++
                ) {

                    String column =
                            columns.get(
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

            if (!columns.isEmpty()) {

                sheet.setAutoFilter(
                        new org.apache.poi.ss.util.CellRangeAddress(
                                2,
                                Math.max(
                                        2,
                                        rowNumber - 1
                                ),
                                0,
                                columns.size() - 1
                        )
                );
            }


            // ====================================================
            // COLUMN WIDTHS
            // ====================================================

            for (
                    int i = 0;
                    i < columns.size();
                    i++
            ) {

                sheet.autoSizeColumn(i);

                int currentWidth =
                        sheet.getColumnWidth(i);

                int minimumWidth =
                        3500;

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


            // Specific wider columns

            for (
                    int i = 0;
                    i < columns.size();
                    i++
            ) {

                String column =
                        columns.get(i);

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
            }


            // ====================================================
            // WRITE
            // ====================================================

            workbook.write(
                    output
            );

            return output.toByteArray();

        } catch (Exception exception) {

            throw new RuntimeException(
                    "Failed to generate Excel export: "
                            + exception.getMessage(),
                    exception
            );
        }
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


        if (
                value instanceof Byte
                        ||
                value instanceof Short
                        ||
                value instanceof Integer
                        ||
                value instanceof Long
        ) {

            cell.setCellValue(
                    ((Number) value)
                            .doubleValue()
            );

            cell.setCellStyle(
                    integerStyle
            );

            return;
        }


        if (
                value instanceof Float
                        ||
                value instanceof Double
        ) {

            cell.setCellValue(
                    ((Number) value)
                            .doubleValue()
            );

            cell.setCellStyle(
                    numberStyle
            );

            return;
        }


        if (value instanceof Number number) {

            cell.setCellValue(
                    number.doubleValue()
            );

            cell.setCellStyle(
                    numberStyle
            );

            return;
        }


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

    public byte[] toPdf(
            String title,
            List<String> columns,
            List<Map<String, Object>> rows,
            String organizationName
    ) {

        try (
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {

            /*
             * CRB reports contain many columns.
             *
             * A4 portrait is too narrow.
             *
             * Landscape gives us much more horizontal space.
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
                            title
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

            Paragraph organizationParagraph =
                    new Paragraph(
                            organizationName == null
                                    ? ""
                                    : organizationName,
                            organizationFont
                    );

            organizationParagraph.setSpacingAfter(
                    3
            );

            document.add(
                    organizationParagraph
            );


            // ====================================================
            // TITLE
            // ====================================================

            Paragraph titleParagraph =
                    new Paragraph(
                            title == null
                                    ? "Report"
                                    : title,
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

            Paragraph generatedParagraph =
                    new Paragraph(
                            "Generated: "
                                    + LocalDateTime.now()
                                    .format(
                                            DATE_TIME_FORMAT
                                    )
                                    + "    |    Records: "
                                    + rows.size(),
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
                            columns.size()
                    );


            table.setWidthPercentage(
                    100
            );


            table.setHeaderRows(
                    1
            );


            /*
             * Explicit relative widths prevent OpenPDF from giving
             * every column the same amount of space.
             */
            float[] widths =
                    buildPdfColumnWidths(
                            columns
                    );


            if (
                    widths.length ==
                            columns.size()
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
                            columns
            ) {

                PdfPCell cell =
                        new PdfPCell(
                                new Phrase(
                                        column,
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
                    Map<String, Object> rowData :
                            rows
            ) {

                alternate =
                        !alternate;


                for (
                        String column :
                                columns
                ) {

                    Object value =
                            rowData.get(
                                    column
                            );


                    PdfPCell cell =
                            new PdfPCell(
                                    new Phrase(
                                            formatCell(
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
                    "Failed to generate PDF export: "
                            + exception.getMessage(),
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

        float[] widths =
                new float[columns.size()];


        for (
                int i = 0;
                i < columns.size();
                i++
        ) {

            String column =
                    columns.get(i);


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
    // CELL FORMAT
    // ============================================================

    private String formatCell(
            Object value
    ) {

        if (value == null) {
            return "";
        }


        if (value instanceof LocalDate date) {

            return date.format(
                    DATE_FORMAT
            );
        }


        if (
                value instanceof LocalDateTime dateTime
        ) {

            return dateTime.format(
                    DATE_TIME_FORMAT
            );
        }


        if (
                value instanceof Double
                        ||
                value instanceof Float
        ) {

            return MONEY_FORMAT.format(
                    ((Number) value)
                            .doubleValue()
            );
        }


        if (
                value instanceof Integer
                        ||
                value instanceof Long
                        ||
                value instanceof Short
                        ||
                value instanceof Byte
        ) {

            return INTEGER_FORMAT.format(
                    ((Number) value)
                            .longValue()
            );
        }


        return value.toString();
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
                            ? "Report"
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


            com.lowagie.text.pdf.ColumnText.showTextAligned(
                    writer.getDirectContent(),
                    Element.ALIGN_CENTER,
                    phrase,
                    (
                            document.left()
                                    + document.right()
                    ) / 2,
                    18,
                    0
            );
        }
    }
}
