package eu.hhmmss.app.converter;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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

    /**
     * Parses a period string in MM/YYYY format and returns the number of days in that month.
     *
     * @param period the period in MM/YYYY format (e.g., "01/2024")
     * @return number of days in the month, or -1 if parsing fails
     */
    private static int getDaysInMonth(String period) {
        try {
            // Parse MM/YYYY format
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/yyyy");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            return yearMonth.lengthOfMonth();
        } catch (Exception e) {
            log.warn("Could not parse period '{}' to determine days in month", period);
            return -1;
        }
    }

    /**
     * Updates the period field in an Excel timesheet file and adjusts day rows.
     * Finds the "Period (month/year):" label in column B and updates the value to its right.
     * Also clears task data for days beyond the month's length.
     *
     * @param xlsxPath path to the Excel file to update
     * @param newPeriod the new period value to set (in MM/YYYY format)
     * @throws IOException if file cannot be read or written
     */
    public static void updatePeriod(Path xlsxPath, String newPeriod) throws IOException {
        try (InputStream in = new FileInputStream(xlsxPath.toFile());
             Workbook wb = new XSSFWorkbook(in)) {

            Sheet sheet = wb.getSheet("Timesheet");
            if (sheet == null) {
                log.warn("Sheet 'Timesheet' not found in Excel: {}", xlsxPath);
                return;
            }

            // Find and update the period value
            boolean updated = false;
            for (Row r : sheet) {
                Cell labelCell = r.getCell(1); // column B
                if (labelCell != null && "Period (month/year):".equals(getCellString(labelCell))) {
                    // Find the first non-empty cell to the right and update it
                    for (int i = 2; i <= Math.max(r.getLastCellNum(), 10); i++) {
                        Cell valueCell = r.getCell(i);
                        if (valueCell == null) {
                            valueCell = r.createCell(i);
                        }
                        String currentValue = getCellString(valueCell).trim();
                        if (!currentValue.isEmpty() || i == 2) {
                            // Update this cell
                            valueCell.setCellValue(newPeriod);
                            updated = true;
                            log.info("Updated period in Excel from '{}' to '{}'", currentValue, newPeriod);
                            break;
                        }
                    }
                    break;
                }
            }

            if (!updated) {
                log.warn("Could not find 'Period (month/year):' field in Excel to update");
                return;
            }

            // Determine number of days in the month
            int daysInMonth = getDaysInMonth(newPeriod);
            if (daysInMonth > 0) {
                adjustDayRows(sheet, daysInMonth);
            }

            // Write back to file
            try (FileOutputStream out = new FileOutputStream(xlsxPath.toFile())) {
                wb.write(out);
                log.info("Successfully updated period in Excel file: {}", xlsxPath);
            }
        }
    }

    /**
     * Adjusts day rows in the Excel timesheet to match the number of days in the month.
     * Clears task and hours data for days beyond the month's length.
     *
     * @param sheet the Excel sheet containing the timesheet
     * @param daysInMonth the number of days in the selected month
     */
    private static void adjustDayRows(Sheet sheet, int daysInMonth) {
        // Find header row where column B equals "Day"
        int headerRow = -1;
        for (Row r : sheet) {
            Cell c = r.getCell(1); // column B
            if (c != null && "Day".equals(getCellString(c).trim())) {
                headerRow = r.getRowNum();
                break;
            }
        }

        if (headerRow < 0) {
            log.warn("Could not find timesheet header row (column B == 'Day') to adjust days");
            return;
        }

        log.info("Adjusting Excel to show {} days (clearing days {} to 31)", daysInMonth, daysInMonth + 1);

        // Clear data for days beyond the month's length
        for (int day = daysInMonth + 1; day <= 31; day++) {
            int rowIndex = headerRow + day;
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                // Clear the day number cell (column B)
                Cell dayCell = row.getCell(1);
                if (dayCell != null) {
                    String dayStr = getCellString(dayCell).trim();
                    // Only clear if it matches the expected day number
                    if (dayStr.equals(day + ".0") || dayStr.equals(String.valueOf(day))) {
                        dayCell.setBlank();
                    }
                }

                // Clear task cell (column C - COL_TASK)
                Cell taskCell = row.getCell(COL_TASK);
                if (taskCell != null) {
                    taskCell.setBlank();
                }

                // Clear hours cell (column D - COL_HOURS)
                Cell hoursCell = row.getCell(COL_HOURS);
                if (hoursCell != null) {
                    hoursCell.setBlank();
                }
            }
        }

        log.info("Cleared task data for days {} to 31", daysInMonth + 1);
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

}
