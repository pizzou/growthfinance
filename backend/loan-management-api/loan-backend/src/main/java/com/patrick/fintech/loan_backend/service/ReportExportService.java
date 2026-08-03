
package com.patrick.fintech.loan_backend.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
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
    // EXCEL EXPORT
    // ============================================================

    public byte[] toExcel(
            String title,
            List<String> columns,
            List<Map<String, Object>> rows
    ) {

        try (
                Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()
        ) {

            String safeSheetName =
                    createSafeSheetName(title);

            Sheet sheet =
                    workbook.createSheet(
                            safeSheetName
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

            CellStyle textStyle =
                    createExcelTextStyle(
                            workbook
                    );

            CellStyle numberStyle =
                    createExcelNumberStyle(
                            workbook
                    );

            CellStyle integerStyle =
                    createExcelIntegerStyle(
                            workbook
                    );

            CellStyle dateStyle =
                    createExcelDateStyle(
                            workbook
                    );


            // ====================================================
            // TITLE
            // ====================================================

            Row titleRow =
                    sheet.createRow(0);

            titleRow.setHeightInPoints(28);

            Cell titleCell =
                    titleRow.createCell(0);

            titleCell.setCellValue(
                    title != null
                            ? title
                            : "Report"
            );

            titleCell.setCellStyle(
                    titleStyle
            );


            if (columns != null && !columns.isEmpty()) {

                sheet.addMergedRegion(
                        new CellRangeAddress(
                                0,
                                0,
                                0,
                                columns.size() - 1
                        )
                );
            }


            // ====================================================
            // HEADER
            // ====================================================

            Row headerRow =
                    sheet.createRow(2);

            headerRow.setHeightInPoints(32);

            if (columns != null) {

                for (
                        int i = 0;
                        i < columns.size();
                        i++
                ) {

                    Cell cell =
                            headerRow.createCell(i);

                    cell.setCellValue(
                            columns.get(i)
                    );

                    cell.setCellStyle(
                            headerStyle
                    );
                }
            }


            // ====================================================
            // DATA
            // ====================================================

            int rowIndex = 3;

            if (rows != null) {

                for (
                        Map<String, Object> rowData
                        : rows
                ) {

                    Row row =
                            sheet.createRow(
                                    rowIndex++
                            );

                    row.setHeightInPoints(
                            24
                    );

                    int columnIndex = 0;

                    if (columns == null) {
                        continue;
                    }

                    for (
                            String column
                            : columns
                    ) {

                        Object value =
                                rowData != null
                                        ? rowData.get(column)
                                        : null;

                        Cell cell =
                                row.createCell(
                                        columnIndex++
                                );

                        writeExcelValue(
                                cell,
                                value,
                                textStyle,
                                numberStyle,
                                integerStyle,
                                dateStyle
                        );
                    }
                }
            }


            // ====================================================
            // FILTER
            // ====================================================

            if (
                    columns != null
                            &&
                    !columns.isEmpty()
            ) {

                int lastRow =
                        Math.max(
                                2,
                                rowIndex - 1
                        );

                sheet.setAutoFilter(
                        new CellRangeAddress(
                                2,
                                lastRow,
                                0,
                                columns.size() - 1
                        )
                );
            }


            // ====================================================
            // FREEZE HEADER
            // ====================================================

            sheet.createFreezePane(
                    0,
                    3
            );


            // ====================================================
            // COLUMN WIDTHS
            // ====================================================

            if (columns != null) {

                for (
                        int i = 0;
                        i < columns.size();
                        i++
                ) {

                    int width =
                            calculateExcelColumnWidth(
                                    columns.get(i),
                                    rows
                            );

                    sheet.setColumnWidth(
                            i,
                            width
                    );
                }
            }


            // ====================================================
            // PRINT SETTINGS
            // ====================================================

            sheet.getPrintSetup()
                    .setLandscape(true);

            sheet.getPrintSetup()
                    .setFitWidth((short) 1);

            sheet.getPrintSetup()
                    .setFitHeight((short) 0);

            sheet.setFitToPage(true);

            sheet.setRepeatingRows(
                    new CellRangeAddress(
                            2,
                            2,
                            -1,
                            -1
                    )
            );


            // ====================================================
            // WRITE
            // ====================================================

            workbook.write(out);

            return out.toByteArray();

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to generate Excel export: "
                            + e.getMessage(),
                    e
            );
        }
    }


    // ============================================================
    // PDF EXPORT
    // ============================================================

    public byte[] toPdf(
            String title,
            List<String> columns,
            List<Map<String, Object>> rows,
            String orgName
    ) {

        try (
                ByteArrayOutputStream out =
                        new ByteArrayOutputStream()
        ) {

            /*
             * IMPORTANT:
             *
             * CRB reports contain many columns.
             *
             * A4 portrait makes the table extremely compressed.
             *
             * Landscape is therefore used automatically.
             */

            boolean wideReport =
                    columns != null
                            &&
                    columns.size() >= 8;


            com.lowagie.text.Rectangle pageSize =
                    wideReport
                            ? PageSize.A4.rotate()
                            : PageSize.A4;


            Document document =
                    new Document(
                            pageSize,
                            24,
                            24,
                            60,
                            42
                    );


            PdfWriter writer =
                    PdfWriter.getInstance(
                            document,
                            out
                    );


            // ====================================================
            // PAGE NUMBER EVENT
            // ====================================================

            writer.setPageEvent(
                    new PdfPageNumberEvent()
            );


            document.open();


            // ====================================================
            // FONTS
            // ====================================================

            com.lowagie.text.Font titleFont =
                    new com.lowagie.text.Font(
                            com.lowagie.text.Font.HELVETICA,
                            17,
                            com.lowagie.text.Font.BOLD,
                            new Color(
                                    15,
                                    23,
                                    42
                            )
                    );


            com.lowagie.text.Font organizationFont =
                    new com.lowagie.text.Font(
                            com.lowagie.text.Font.HELVETICA,
                            11,
                            com.lowagie.text.Font.BOLD,
                            new Color(
                                    51,
                                    65,
                                    85
                            )
                    );


            com.lowagie.text.Font dateFont =
                    new com.lowagie.text.Font(
                            com.lowagie.text.Font.HELVETICA,
                            9,
                            com.lowagie.text.Font.NORMAL,
                            new Color(
                                    100,
                                    116,
                                    139
                            )
                    );


            com.lowagie.text.Font headerFont =
                    new com.lowagie.text.Font(
                            com.lowagie.text.Font.HELVETICA,
                            wideReport ? 7.5f : 9f,
                            com.lowagie.text.Font.BOLD,
                            Color.WHITE
                    );


            com.lowagie.text.Font bodyFont =
                    new com.lowagie.text.Font(
                            com.lowagie.text.Font.HELVETICA,
                            wideReport ? 7.5f : 8.5f,
                            com.lowagie.text.Font.NORMAL,
                            new Color(
                                    30,
                                    41,
                                    59
                            )
                    );


            // ====================================================
            // REPORT HEADER
            // ====================================================

            if (
                    orgName != null
                            &&
                    !orgName.isBlank()
            ) {

                Paragraph organizationParagraph =
                        new Paragraph(
                                orgName,
                                organizationFont
                        );

                organizationParagraph.setSpacingAfter(
                        3
                );

                document.add(
                        organizationParagraph
                );
            }


            Paragraph titleParagraph =
                    new Paragraph(
                            title != null
                                    ? title
                                    : "Report",
                            titleFont
                    );

            titleParagraph.setSpacingAfter(
                    4
            );

            document.add(
                    titleParagraph
            );


            Paragraph generatedParagraph =
                    new Paragraph(
                            "Generated: "
                                    + LocalDateTime.now()
                                    .format(
                                            DATE_TIME_FORMAT
                                    ),
                            dateFont
                    );

            generatedParagraph.setSpacingAfter(
                    12
            );

            document.add(
                    generatedParagraph
            );


            // ====================================================
            // EMPTY REPORT
            // ====================================================

            if (
                    columns == null
                            ||
                    columns.isEmpty()
            ) {

                document.add(
                        new Paragraph(
                                "No report columns available.",
                                bodyFont
                        )
                );

                document.close();

                return out.toByteArray();
            }


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


            table.setSplitRows(
                    true
            );


            table.setSplitLate(
                    false
            );


            // ====================================================
            // COLUMN WIDTHS
            // ====================================================

            float[] widths =
                    calculatePdfColumnWidths(
                            columns
                    );


            try {

                table.setWidths(
                        widths
                );

            } catch (Exception ignored) {

                /*
                 * If a future report has unusual
                 * columns, allow OpenPDF to use
                 * automatic widths.
                 */
            }


            // ====================================================
            // HEADER CELLS
            // ====================================================

            for (
                    String column
                    : columns
            ) {

                PdfPCell headerCell =
                        new PdfPCell(
                                new Phrase(
                                        column != null
                                                ? column
                                                : "",
                                        headerFont
                                )
                        );


                headerCell.setBackgroundColor(
                        new Color(
                                30,
                                41,
                                59
                        )
                );


                headerCell.setHorizontalAlignment(
                        Element.ALIGN_CENTER
                );


                headerCell.setVerticalAlignment(
                        Element.ALIGN_MIDDLE
                );


                headerCell.setPadding(
                        5
                );


                headerCell.setPaddingTop(
                        6
                );


                headerCell.setPaddingBottom(
                        6
                );


                headerCell.setBorderColor(
                        new Color(
                                148,
                                163,
                                184
                        )
                );


                table.addCell(
                        headerCell
                );
            }


            // ====================================================
            // BODY
            // ====================================================

            if (rows != null) {

                int rowNumber = 0;

                for (
                        Map<String, Object> rowData
                        : rows
                ) {

                    boolean alternate =
                            rowNumber % 2 == 1;

                    for (
                            String column
                            : columns
                    ) {

                        Object value =
                                rowData != null
                                        ? rowData.get(column)
                                        : null;


                        String text =
                                formatCell(
                                        value
                                );


                        PdfPCell cell =
                                new PdfPCell(
                                        new Phrase(
                                                text,
                                                bodyFont
                                        )
                                );


                        // ----------------------------------------
                        // ALTERNATING ROWS
                        // ----------------------------------------

                        if (alternate) {

                            cell.setBackgroundColor(
                                    new Color(
                                            248,
                                            250,
                                            252
                                    )
                            );
                        }


                        // ----------------------------------------
                        // PADDING
                        // ----------------------------------------

                        cell.setPadding(
                                4
                        );


                        cell.setPaddingTop(
                                4
                        );


                        cell.setPaddingBottom(
                                4
                        );


                        // ----------------------------------------
                        // ALIGNMENT
                        // ----------------------------------------

                        if (isNumericValue(value)) {

                            cell.setHorizontalAlignment(
                                    Element.ALIGN_RIGHT
                            );

                        } else {

                            cell.setHorizontalAlignment(
                                    Element.ALIGN_LEFT
                            );
                        }


                        cell.setVerticalAlignment(
                                Element.ALIGN_MIDDLE
                        );


                        // ----------------------------------------
                        // BORDERS
                        // ----------------------------------------

                        cell.setBorderColor(
                                new Color(
                                        203,
                                        213,
                                        225
                                )
                        );


                        table.addCell(
                                cell
                        );
                    }

                    rowNumber++;
                }
            }


            // ====================================================
            // TABLE
            // ====================================================

            document.add(
                    table
            );


            // ====================================================
            // FOOTER
            // ====================================================

            Paragraph footer =
                    new Paragraph(
                            "End of report",
                            dateFont
                    );

            footer.setAlignment(
                    Element.ALIGN_CENTER
            );

            footer.setSpacingBefore(
                    10
            );

            document.add(
                    footer
            );


            // ====================================================
            // CLOSE
            // ====================================================

            document.close();

            return out.toByteArray();

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to generate PDF export: "
                            + e.getMessage(),
                    e
            );
        }
    }


    // ============================================================
    // EXCEL VALUE WRITER
    // ============================================================

    private void writeExcelValue(
            Cell cell,
            Object value,
            CellStyle textStyle,
            CellStyle numberStyle,
            CellStyle integerStyle,
            CellStyle dateStyle
    ) {

        if (value == null) {

            cell.setCellValue("");

            cell.setCellStyle(
                    textStyle
            );

            return;
        }


        if (value instanceof Integer) {

            cell.setCellValue(
                    ((Integer) value)
                            .doubleValue()
            );

            cell.setCellStyle(
                    integerStyle
            );

            return;
        }


        if (value instanceof Long) {

            cell.setCellValue(
                    ((Long) value)
                            .doubleValue()
            );

            cell.setCellStyle(
                    integerStyle
            );

            return;
        }


        if (
                value instanceof Double
                        ||
                value instanceof Float
                        ||
                value instanceof Number
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
                    dateStyle
            );

            return;
        }


        cell.setCellValue(
                value.toString()
        );

        cell.setCellStyle(
                textStyle
        );
    }


    // ============================================================
    // EXCEL STYLES
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

        font.setColor(
                IndexedColors.WHITE.getIndex()
        );

        style.setFont(
                font
        );

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

        return style;
    }


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

        style.setFont(
                font
        );

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

        style.setWrapText(
                true
        );

        style.setBorderBottom(
                BorderStyle.THIN
        );

        style.setBorderTop(
                BorderStyle.THIN
        );

        style.setBorderLeft(
                BorderStyle.THIN
        );

        style.setBorderRight(
                BorderStyle.THIN
        );

        return style;
    }


    private CellStyle createExcelTextStyle(
            Workbook workbook
    ) {

        CellStyle style =
                workbook.createCellStyle();

        style.setVerticalAlignment(
                VerticalAlignment.CENTER
        );

        style.setWrapText(
                true
        );

        style.setBorderBottom(
                BorderStyle.THIN
        );

        style.setBorderTop(
                BorderStyle.THIN
        );

        style.setBorderLeft(
                BorderStyle.THIN
        );

        style.setBorderRight(
                BorderStyle.THIN
        );

        return style;
    }


    private CellStyle createExcelNumberStyle(
            Workbook workbook
    ) {

        CellStyle style =
                createExcelTextStyle(
                        workbook
                );

        style.setAlignment(
                HorizontalAlignment.RIGHT
        );

        style.setDataFormat(
                workbook
                        .createDataFormat()
                        .getFormat(
                                "#,##0.00"
                        )
        );

        return style;
    }


    private CellStyle createExcelIntegerStyle(
            Workbook workbook
    ) {

        CellStyle style =
                createExcelTextStyle(
                        workbook
                );

        style.setAlignment(
                HorizontalAlignment.RIGHT
        );

        style.setDataFormat(
                workbook
                        .createDataFormat()
                        .getFormat(
                                "#,##0"
                        )
        );

        return style;
    }


    private CellStyle createExcelDateStyle(
            Workbook workbook
    ) {

        CellStyle style =
                createExcelTextStyle(
                        workbook
                );

        style.setAlignment(
                HorizontalAlignment.CENTER
        );

        return style;
    }


    // ============================================================
    // PDF COLUMN WIDTHS
    // ============================================================

    private float[] calculatePdfColumnWidths(
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
                    columns.get(i) == null
                            ? ""
                            : columns.get(i)
                            .toLowerCase();


            // --------------------------------------------
            // Borrower information
            // --------------------------------------------

            if (
                    column.contains(
                            "full name"
                    )
            ) {

                widths[i] = 2.4f;

            } else if (
                    column.contains(
                            "national id"
                    )
            ) {

                widths[i] = 2.0f;

            } else if (
                    column.contains(
                            "phone"
                    )
            ) {

                widths[i] = 1.7f;

            } else if (
                    column.contains(
                            "date of birth"
                    )
            ) {

                widths[i] = 1.5f;

            } else if (
                    column.contains(
                            "gender"
                    )
            ) {

                widths[i] = 1.1f;


            // --------------------------------------------
            // Loan information
            // --------------------------------------------

            } else if (
                    column.contains(
                            "loan number"
                    )
            ) {

                widths[i] = 2.2f;

            } else if (
                    column.contains(
                            "loan type"
                    )
            ) {

                widths[i] = 1.7f;

            } else if (
                    column.contains(
                            "status"
                    )
            ) {

                widths[i] = 1.5f;

            } else if (
                    column.contains(
                            "classification"
                    )
            ) {

                widths[i] = 1.7f;


            // --------------------------------------------
            // Financial values
            // --------------------------------------------

            } else if (
                    column.contains(
                            "amount"
                    )
            ) {

                widths[i] = 1.7f;

            } else if (
                    column.contains(
                            "balance"
                    )
            ) {

                widths[i] = 1.8f;

            } else if (
                    column.contains(
                            "score"
                    )
            ) {

                widths[i] = 1.1f;

            } else if (
                    column.contains(
                            "days"
                    )
            ) {

                widths[i] = 1.2f;


            // --------------------------------------------
            // Dates
            // --------------------------------------------

            } else if (
                    column.contains(
                            "date"
                    )
                            ||
                    column.contains(
                            "payment"
                    )
            ) {

                widths[i] = 1.5f;


            // --------------------------------------------
            // Branch
            // --------------------------------------------

            } else if (
                    column.contains(
                            "branch"
                    )
            ) {

                widths[i] = 1.8f;


            // --------------------------------------------
            // Currency
            // --------------------------------------------

            } else if (
                    column.contains(
                            "currency"
                    )
            ) {

                widths[i] = 1.1f;


            // --------------------------------------------
            // Default
            // --------------------------------------------

            } else {

                widths[i] = 1.5f;
            }
        }


        return widths;
    }


    // ============================================================
    // EXCEL COLUMN WIDTH
    // ============================================================

    private int calculateExcelColumnWidth(
            String column,
            List<Map<String, Object>> rows
    ) {

        if (column == null) {
            return 15 * 256;
        }


        String normalized =
                column.toLowerCase();


        int width;


        if (
                normalized.contains(
                        "full name"
                )
        ) {

            width = 28;

        } else if (
                normalized.contains(
                        "national id"
                )
        ) {

            width = 20;

        } else if (
                normalized.contains(
                        "loan number"
                )
        ) {

            width = 22;

        } else if (
                normalized.contains(
                        "phone"
                )
        ) {

            width = 18;

        } else if (
                normalized.contains(
                        "loan type"
                )
        ) {

            width = 18;

        } else if (
                normalized.contains(
                        "status"
                )
        ) {

            width = 18;

        } else if (
                normalized.contains(
                        "classification"
                )
        ) {

            width = 20;

        } else if (
                normalized.contains(
                        "branch"
                )
        ) {

            width = 22;

        } else if (
                normalized.contains(
                        "amount"
                )
                        ||
                normalized.contains(
                        "balance"
                )
            ) {

            width = 18;

        } else if (
                normalized.contains(
                        "date"
                )
                        ||
                normalized.contains(
                        "payment"
                )
            ) {

            width = 16;

        } else if (
                normalized.contains(
                        "gender"
                )
            ) {

            width = 12;

        } else {

            width = 16;
        }


        /*
         * Excel maximum column width is 255.
         *
         * 256 units = approximately one character.
         */

        width =
                Math.min(
                        width,
                        60
                );


        return width * 256;
    }


    // ============================================================
    // CELL FORMATTING
    // ============================================================

    private String formatCell(
            Object value
    ) {

        if (value == null) {
            return "";
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
        ) {

            return INTEGER_FORMAT.format(
                    ((Number) value)
                            .longValue()
            );
        }


        if (
                value instanceof LocalDate date
        ) {

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


        return value.toString();
    }


    // ============================================================
    // NUMERIC CHECK
    // ============================================================

    private boolean isNumericValue(
            Object value
    ) {

        return value instanceof Number;
    }


    // ============================================================
    // SAFE EXCEL SHEET NAME
    // ============================================================

    private String createSafeSheetName(
            String title
    ) {

        if (
                title == null
                        ||
                title.isBlank()
        ) {

            return "Report";
        }


        String name =
                title
                        .replace(
                                "/",
                                "-"
                        )
                        .replace(
                                "\\",
                                "-"
                        )
                        .replace(
                                "?",
                                ""
                        )
                        .replace(
                                "*",
                                ""
                        )
                        .replace(
                                "[",
                                "("
                        )
                        .replace(
                                "]",
                                ")"
                        )
                        .replace(
                                ":",
                                "-"
                        );


        if (name.length() > 31) {

            name =
                    name.substring(
                            0,
                            31
                    );
        }


        if (name.isBlank()) {
            return "Report";
        }


        return name;
    }


    // ============================================================
    // PDF PAGE NUMBER
    // ============================================================

    private static class PdfPageNumberEvent
            extends PdfPageEventHelper {

        private final com.lowagie.text.Font footerFont =
                new com.lowagie.text.Font(
                        com.lowagie.text.Font.HELVETICA,
                        8,
                        com.lowagie.text.Font.NORMAL,
                        new Color(
                                100,
                                116,
                                139
                        )
                );


        @Override
        public void onEndPage(
                PdfWriter writer,
                Document document
        ) {

            String text =
                    "Page "
                            + writer.getPageNumber();


            Phrase phrase =
                    new Phrase(
                            text,
                            footerFont
                    );


            com.lowagie.text.pdf.ColumnText.showTextAligned(
                    writer.getDirectContent(),
                    Element.ALIGN_CENTER,
                    phrase,
                    (
                            document.right()
                                    + document.left()
                    ) / 2,
                    20,
                    0
            );
        }
    }
}
