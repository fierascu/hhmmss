package eu.hhmmss.app.converter;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
public class XlsService {

    public static final int COL_DAY = 1;  // Column B
    public static final int COL_TASK = 2;  // Column C
    public static final int COL_HOURS_FLEXIBILITY = 3;  // Column D
    public static final int COL_HOURS_OUTSIDE_FLEXIBILITY = 4;  // Column E
    public static final int COL_HOURS_SATURDAYS = 5;  // Column F
    public static final int COL_HOURS_SUNDAYS_HOLIDAYS = 6;  // Column G
    public static final int COL_HOURS_STANDBY = 7;  // Column H
    public static final int COL_HOURS_NON_INVOICEABLE = 8;  // Column I

    /**
     * Reads the content of the provided Excel timesheet file (expected sheet name: "Timesheet").
     * Extracts meta fields from the left labels, the daily task (column C), and 6 hours columns:
     * D=Flexibility period, E=Outside flexibility, F=Saturdays, G=Sundays/holidays, H=Standby, I=Non-invoiceable.
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

            // Signature fields
            meta.put("Date and signature:", findRightSideValue(sheet, "Date and signature:"));
            meta.put("Additional comments:", findRightSideValue(sheet, "Additional comments:"));
            meta.put("Official responsible for acceptance:", findRightSideValue(sheet, "Official responsible for acceptance:"));
            meta.put("Date (acceptance):", findRightSideValue(sheet, "Date (acceptance):"));

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


            // Read day rows: B=Day, C=Tasks, D-I=Hours columns
            for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String dayStr = getCellString(row.getCell(COL_DAY)).trim();

                if (!dayStr.endsWith(".0")) continue;

                double v = Double.parseDouble(dayStr);
                int day = (int) v;
                String task = getCellString(row.getCell(COL_TASK));
                double hoursFlexibility = getCellNumeric(row.getCell(COL_HOURS_FLEXIBILITY));
                double hoursOutsideFlexibility = getCellNumeric(row.getCell(COL_HOURS_OUTSIDE_FLEXIBILITY));
                double hoursSaturdays = getCellNumeric(row.getCell(COL_HOURS_SATURDAYS));
                double hoursSundaysHolidays = getCellNumeric(row.getCell(COL_HOURS_SUNDAYS_HOLIDAYS));
                double hoursStandby = getCellNumeric(row.getCell(COL_HOURS_STANDBY));
                double hoursNonInvoiceable = getCellNumeric(row.getCell(COL_HOURS_NON_INVOICEABLE));

                DayData dayData = DayData.builder()
                        .task(task)
                        .hoursFlexibilityPeriod(hoursFlexibility)
                        .hoursOutsideFlexibilityPeriod(hoursOutsideFlexibility)
                        .hoursSaturdays(hoursSaturdays)
                        .hoursSundaysHolidays(hoursSundaysHolidays)
                        .hoursStandby(hoursStandby)
                        .hoursNonInvoiceable(hoursNonInvoiceable)
                        .build();

                hhmmssDto.getTasks().put(day, dayData);
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
     * Applies yellow highlighting to weekend and holiday cells in an Excel timesheet.
     * Can be called independently to highlight an existing file.
     *
     * @param xlsxPath path to the Excel file to update
     * @throws IOException if file cannot be read or written
     */
    public static void highlightWeekendsAndHolidaysInFile(Path xlsxPath) throws IOException {
        try (InputStream in = new FileInputStream(xlsxPath.toFile());
             Workbook wb = new XSSFWorkbook(in)) {

            Sheet sheet = wb.getSheet("Timesheet");
            if (sheet == null) {
                log.warn("Sheet 'Timesheet' not found in Excel: {}", xlsxPath);
                return;
            }

            // Get the period from the file
            String period = findRightSideValue(sheet, "Period (month/year):");
            if (period == null || period.trim().isEmpty()) {
                log.warn("No period found in Excel file, cannot determine dates for highlighting");
                return;
            }

            // Parse period to MM/YYYY format if needed (handle various formats)
            String formattedPeriod = parsePeriodToMMYYYY(period);
            if (formattedPeriod == null) {
                log.warn("Could not parse period '{}' for highlighting", period);
                return;
            }

            highlightWeekendsAndHolidays(wb, sheet, formattedPeriod);

            // Write back to file
            try (FileOutputStream out = new FileOutputStream(xlsxPath.toFile())) {
                wb.write(out);
                log.info("Successfully highlighted weekends/holidays in Excel file: {}", xlsxPath);
            }
        }
    }

    /**
     * Attempts to parse various period formats to MM/YYYY format.
     * Handles: "MM/YYYY", "January 2024", "01-2024", etc.
     *
     * @param period the period string to parse
     * @return formatted period as MM/YYYY, or null if parsing fails
     */
    private static String parsePeriodToMMYYYY(String period) {
        if (period == null || period.trim().isEmpty()) {
            return null;
        }

        String trimmed = period.trim();

        // Already in MM/YYYY format
        if (trimmed.matches("\\d{2}/\\d{4}")) {
            return trimmed;
        }

        // Try to parse "January 2024" format
        try {
            String[] parts = trimmed.split(" ");
            if (parts.length == 2) {
                String monthName = parts[0];
                String year = parts[1];

                // Convert month name to number
                int monthNum = switch (monthName.toLowerCase()) {
                    case "january" -> 1;
                    case "february" -> 2;
                    case "march" -> 3;
                    case "april" -> 4;
                    case "may" -> 5;
                    case "june" -> 6;
                    case "july" -> 7;
                    case "august" -> 8;
                    case "september" -> 9;
                    case "october" -> 10;
                    case "november" -> 11;
                    case "december" -> 12;
                    default -> -1;
                };

                if (monthNum > 0) {
                    return String.format("%02d/%s", monthNum, year);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse period as month name format: {}", period);
        }

        return null;
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
                    // Find the first non-empty cell to the right (where the value currently is)
                    Cell valueCell = null;
                    int valueCellIndex = -1;
                    for (int i = 2; i <= Math.max(r.getLastCellNum(), 10); i++) {
                        Cell cell = r.getCell(i);
                        if (cell != null) {
                            String cellValue = getCellString(cell).trim();
                            if (!cellValue.isEmpty()) {
                                valueCell = cell;
                                valueCellIndex = i;
                                break;
                            }
                        }
                    }

                    // If no existing value found, create one TWO cells to the right of the label
                    // (label in column B, skip column C, value in column D)
                    if (valueCell == null) {
                        valueCellIndex = labelCell.getColumnIndex() + 2;
                        valueCell = r.getCell(valueCellIndex);
                        if (valueCell == null) {
                            valueCell = r.createCell(valueCellIndex);
                        }
                    }

                    String currentValue = getCellString(valueCell).trim();
                    valueCell.setCellValue(newPeriod);
                    updated = true;
                    log.info("Updated period in Excel from '{}' to '{}' at column {}", currentValue, newPeriod, valueCellIndex);
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

            // Highlight weekends and holidays
            highlightWeekendsAndHolidays(wb, sheet, newPeriod);

            // Write back to file
            try (FileOutputStream out = new FileOutputStream(xlsxPath.toFile())) {
                wb.write(out);
                log.info("Successfully updated period in Excel file: {}", xlsxPath);
            }
        }
    }

    /**
     * Adjusts day rows in the Excel timesheet to match the number of days in the month.
     * Adds missing day rows and clears task/hours data for days beyond the month's length.
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

        log.info("Adjusting Excel to show {} days", daysInMonth);

        // First, ensure all day rows exist (add missing ones)
        for (int day = 1; day <= daysInMonth; day++) {
            int rowIndex = headerRow + day;
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
                log.info("Created missing row for day {}", day);
            }

            // Ensure day number cell exists
            Cell dayCell = row.getCell(COL_DAY);
            if (dayCell == null || getCellString(dayCell).trim().isEmpty()) {
                if (dayCell == null) {
                    dayCell = row.createCell(COL_DAY);
                }
                dayCell.setCellValue((double) day);
                log.info("Set day number {} in row {}", day, rowIndex);
            }
        }

        // Then, clear data for days beyond the month's length
        for (int day = daysInMonth + 1; day <= 31; day++) {
            int rowIndex = headerRow + day;
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                // Clear the day number cell (column B)
                Cell dayCell = row.getCell(COL_DAY);
                if (dayCell != null) {
                    String dayStr = getCellString(dayCell).trim();
                    // Only clear if it matches the expected day number
                    if (dayStr.equals(day + ".0") || dayStr.equals(String.valueOf(day))) {
                        dayCell.setBlank();
                    }
                }

                // Clear task cell (column C)
                Cell taskCell = row.getCell(COL_TASK);
                if (taskCell != null) {
                    taskCell.setBlank();
                }

                // Clear all hours columns (columns D-I)
                for (int colIdx = COL_HOURS_FLEXIBILITY; colIdx <= COL_HOURS_NON_INVOICEABLE; colIdx++) {
                    Cell hoursCell = row.getCell(colIdx);
                    if (hoursCell != null) {
                        hoursCell.setBlank();
                    }
                }
            }
        }

        log.info("Adjusted day rows: added/verified days 1-{}, cleared days {} to 31", daysInMonth, daysInMonth + 1);
    }

    /**
     * Highlights weekend and holiday cells with a yellow background.
     * Applies to the day column (column B) for all days in the specified period.
     *
     * @param wb the workbook containing the sheet
     * @param sheet the Excel sheet containing the timesheet
     * @param period the period in MM/YYYY format
     */
    private static void highlightWeekendsAndHolidays(Workbook wb, Sheet sheet, String period) {
        try {
            // Parse the period to get year and month
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/yyyy");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            int year = yearMonth.getYear();
            int month = yearMonth.getMonthValue();
            int daysInMonth = yearMonth.lengthOfMonth();

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
                log.warn("Could not find timesheet header row to highlight weekends/holidays");
                return;
            }

            // Create yellow fill style
            CellStyle yellowStyle = wb.createCellStyle();
            yellowStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            yellowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Load holidays (using static method since this is a static context)
            HolidayService holidayService = new HolidayService();

            int highlightedCount = 0;

            // Process each day in the month
            for (int day = 1; day <= daysInMonth; day++) {
                LocalDate date = LocalDate.of(year, month, day);

                // Check if this day is a weekend or holiday
                if (HolidayService.isWeekend(date) || holidayService.isHoliday(date)) {
                    Row row = sheet.getRow(headerRow + day);
                    if (row != null) {
                        // Apply yellow background to day cell (column B)
                        Cell dayCell = row.getCell(COL_DAY);
                        if (dayCell != null) {
                            // Preserve existing cell value and type, just change the style
                            CellStyle newStyle = wb.createCellStyle();
                            newStyle.cloneStyleFrom(dayCell.getCellStyle());
                            newStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
                            newStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                            dayCell.setCellStyle(newStyle);
                            highlightedCount++;
                        }
                    }
                }
            }

            log.info("Highlighted {} weekend/holiday days in Excel", highlightedCount);

        } catch (Exception e) {
            log.warn("Failed to highlight weekends/holidays in Excel", e);
            // Don't fail the whole operation if highlighting fails
        }
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
