package eu.hhmmss.app.converter;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
@Service
public class XlsService {

    public static final int COL_DAY = 2;
    public static final int COL_TASK = COL_DAY + 1;
    public static final int COL_HOURS = COL_TASK + 1;

    /**
     * Reads the content of the provided Excel timesheet file (expected sheet name: "Timesheet").
     * Extracts meta fields from the left labels, the daily task (column C), and daily hours from the
     * column whose header contains "during flexibility period".
     *
     * @param xlsxPath path to the Excel file, e.g. Path.of("timesheet-in.xlsx")
     * @return parsed timesheet data
     * @throws IOException           if file is missing or not readable
     * @throws IllegalStateException if the expected sheet/headers are not found
     */
    public static HhmmssDto readTimesheet(Path xlsxPath) {
        HhmmssDto hhmmssDto = new HhmmssDto();
        Map<String, String> meta = hhmmssDto.getMeta();

        try (InputStream in = new FileInputStream(xlsxPath.toFile());
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheet("Timesheet");
            if (sheet == null) throw new IllegalStateException("Sheet 'Timesheet' not found in Excel: " + xlsxPath);

            // Meta values: search for labels in column B, take first non-empty value to the right
            meta.put("Specific Contract Reference:", findRightSideValue(sheet, "Specific Contract Reference:"));
            meta.put("Purchase Order no (PO):", findRightSideValue(sheet, "Purchase Order no (PO):"));
            meta.put("Period (month/year):", findRightSideValue(sheet, "Period (month/year):"));
            meta.put("Family Name of person:", findRightSideValue(sheet, "Family Name of person:"));
            meta.put("First Name of person:", findRightSideValue(sheet, "First Name of person:"));
            meta.put("Profile - Seniority level:", findRightSideValue(sheet, "Profile - Seniority level:"));

            // Find header row where column B equals "Day"
            int headerRow = -1;
            for (Row r : sheet) {
                Cell c = r.getCell(1); // column B
                if (c != null && "Day".equals(getCellString(c).trim())) {
                    headerRow = r.getRowNum();
                    // eg 11
                    break;
                }
            }
            if (headerRow < 0)
                throw new IllegalStateException("Could not find timesheet header row (column B == 'Day').");


            // Read day rows: B=Day, C=Tasks, hours from detected column
            for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String dayStr = getCellString(row.getCell(1)).trim();

                if (!dayStr.endsWith(".0")) continue;

                double v = Double.parseDouble(dayStr);
                int day = (int) v;
                String colC = getCellString(row.getCell(2));
                Double colD = getCellNumeric(row.getCell(3));
                hhmmssDto.getTasks().put(day, new ImmutablePair<>(colC, colD));
            }
        } catch (FileNotFoundException e) {
            log.error("File not found: {}", xlsxPath, e);
        } catch (IOException e) {
            log.error("Error reading file: {}", xlsxPath, e);
        }

        log.info("xlsxFormat: {}", hhmmssDto);
        return hhmmssDto;
    }

    private static String getCellString(Cell c) {
        if (c == null) return "";
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue();
            case NUMERIC -> (DateUtil.isCellDateFormatted(c) ? c.getDateCellValue().toString()
                    : Double.toString(c.getNumericCellValue()));
            case BOOLEAN -> Boolean.toString(c.getBooleanCellValue());
            case FORMULA -> {
                try {
                    FormulaEvaluator eval = c.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                    CellValue v = eval.evaluate(c);
                    if (v == null) yield "";
                    yield switch (v.getCellType()) {
                        case STRING -> v.getStringValue();
                        case NUMERIC -> Double.toString(v.getNumberValue());
                        case BOOLEAN -> Boolean.toString(v.getBooleanValue());
                        default -> "";
                    };
                } catch (Exception e) {
                    yield c.getCellFormula();
                }
            }
            default -> "";
        };
    }

    private static double getCellNumeric(Cell c) {
        if (c == null) return 0.0;
        try {
            if (c.getCellType() == CellType.NUMERIC) return c.getNumericCellValue();
            String s = getCellString(c);
            if (s == null || s.isBlank()) return 0.0;
            s = s.replace(" ", "").replace("\u00A0", "").replace(",", ".");
            return Double.parseDouble(s);
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    /**
     * Finds a value to the right of the given label located in column B.
     */
    private static String findRightSideValue(Sheet sheet, String labelB) {
        for (Row r : sheet) {
            Cell c = r.getCell(1); // column B
            if (c != null && labelB.equals(getCellString(c))) {
                for (int i = 2; i <= Math.max(r.getLastCellNum(), 10); i++) { // scan a few cells to the right
                    Cell v = r.getCell(i);
                    if (v != null) {
                        String s = getCellString(v).trim();
                        if (!s.isEmpty()) return s;
                    }
                }
            }
        }
        return "";
    }

    /**
     * Writes the extracted hhmmss data to a new Excel file.
     * Similar to DocService.addDataToDocs() but for Excel format.
     *
     * @param hhmmssDto the parsed timesheet data
     * @param outXlsx path where the output Excel file should be written
     * @return the output path
     */
    public static Path writeExtractedData(HhmmssDto hhmmssDto, Path outXlsx) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Extracted Data");

            int rowNum = 0;

            // Create title
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Extracted HHMMSS Data");
            CellStyle titleStyle = createTitleStyle(workbook);
            titleCell.setCellStyle(titleStyle);

            rowNum++; // Empty row

            // Write metadata section
            Row metaHeaderRow = sheet.createRow(rowNum++);
            Cell metaHeaderCell = metaHeaderRow.createCell(0);
            metaHeaderCell.setCellValue("Metadata");
            CellStyle headerStyle = createHeaderStyle(workbook);
            metaHeaderCell.setCellStyle(headerStyle);

            // Write each metadata entry
            for (Map.Entry<String, String> entry : hhmmssDto.getMeta().entrySet()) {
                Row metaRow = sheet.createRow(rowNum++);
                metaRow.createCell(0).setCellValue(entry.getKey());
                metaRow.createCell(1).setCellValue(entry.getValue());
            }

            rowNum++; // Empty row

            // Write tasks section
            Row tasksHeaderRow = sheet.createRow(rowNum++);
            tasksHeaderRow.createCell(0).setCellValue("Day");
            tasksHeaderRow.createCell(1).setCellValue("Task");
            tasksHeaderRow.createCell(2).setCellValue("Hours");
            tasksHeaderRow.createCell(3).setCellValue("HH:MM:SS");

            // Apply header style
            for (int i = 0; i < 4; i++) {
                tasksHeaderRow.getCell(i).setCellStyle(headerStyle);
            }

            // Write task data sorted by day
            hhmmssDto.getTasks().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    int currentRow = sheet.getLastRowNum() + 1;
                    Row taskRow = sheet.createRow(currentRow);

                    Integer day = entry.getKey();
                    Pair<String, Double> taskData = entry.getValue();
                    String taskDescription = taskData.getLeft();
                    Double hours = taskData.getRight();

                    taskRow.createCell(0).setCellValue(day);
                    taskRow.createCell(1).setCellValue(taskDescription);
                    taskRow.createCell(2).setCellValue(hours != null ? hours : 0.0);
                    taskRow.createCell(3).setCellValue(convertHoursToHhmmss(hours));
                });

            // Auto-size columns
            for (int i = 0; i < 4; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to file
            try (FileOutputStream fileOut = new FileOutputStream(outXlsx.toFile())) {
                workbook.write(fileOut);
                log.info("Wrote extracted data to: {}", outXlsx.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Could not write to {}", outXlsx, e);
        }

        return outXlsx;
    }

    /**
     * Converts hours (as double) to HH:MM:SS format
     */
    private static String convertHoursToHhmmss(Double hours) {
        if (hours == null || hours == 0.0) {
            return "00:00:00";
        }

        int totalSeconds = (int) Math.round(hours * 3600);
        int h = totalSeconds / 3600;
        int m = (totalSeconds % 3600) / 60;
        int s = totalSeconds % 60;

        return String.format("%02d:%02d:%02d", h, m, s);
    }

    /**
     * Creates a style for title cells
     */
    private static CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    /**
     * Creates a style for header cells
     */
    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

}
