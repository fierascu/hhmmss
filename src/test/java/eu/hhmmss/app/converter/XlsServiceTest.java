package eu.hhmmss.app.converter;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class XlsServiceTest {

    private XlsService xlsService;

    @BeforeEach
    void setUp() {
        // Initialize XlsService with HolidayService containing 2025 and 2026 holidays
        String holidays = "2025-01-01,2025-04-18,2025-04-21,2025-05-01,2025-12-25,2025-12-26," +
                         "2026-01-01,2026-04-03,2026-04-06,2026-05-01,2026-12-25,2026-12-26";
        String epLongFridays = "";
        HolidayService holidayService = new HolidayService(holidays, epLongFridays);
        xlsService = new XlsService(holidayService);
    }

    @Test
    void testReadTimesheetWithValidFile() {
        Path xlsxFilePath = Path.of("src/test/resources/timesheet-in.xlsx");
        HhmmssDto result = XlsService.readTimesheet(xlsxFilePath);

        assertNotNull(result);
        assertNotNull(result.getMeta());
        assertNotNull(result.getTasks());

        // Verify meta fields are populated
        assertFalse(result.getMeta().isEmpty());

        // Verify tasks are populated
        assertFalse(result.getTasks().isEmpty());
    }

    @Test
    void testReadTimesheetMetaFields(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("test-meta.xlsx");

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Timesheet");

            // Create meta fields
            createMetaRow(sheet, 0, "Specific Contract Reference:", "CONTRACT-123");
            createMetaRow(sheet, 1, "Purchase Order no (PO):", "PO-456");
            createMetaRow(sheet, 2, "Period (month/year):", "January 2025");
            createMetaRow(sheet, 3, "Family Name of person:", "Doe");
            createMetaRow(sheet, 4, "First Name of person:", "John");
            createMetaRow(sheet, 5, "Profile - Seniority level:", "Senior");

            // Create header row
            Row headerRow = sheet.createRow(10);
            headerRow.createCell(1).setCellValue("Day");
            headerRow.createCell(2).setCellValue("Tasks");
            headerRow.createCell(3).setCellValue("Hours");

            // Create data rows
            createDataRow(sheet, 11, 1, "Development", 8.0);
            createDataRow(sheet, 12, 2, "Testing", 7.5);

            try (FileOutputStream fos = new FileOutputStream(testFile.toFile())) {
                wb.write(fos);
            }
        }

        HhmmssDto result = XlsService.readTimesheet(testFile);

        assertNotNull(result);
        assertEquals("CONTRACT-123", result.getMeta().get("Specific Contract Reference:"));
        assertEquals("PO-456", result.getMeta().get("Purchase Order no (PO):"));
        assertEquals("January 2025", result.getMeta().get("Period (month/year):"));
        assertEquals("Doe", result.getMeta().get("Family Name of person:"));
        assertEquals("John", result.getMeta().get("First Name of person:"));
        assertEquals("Senior", result.getMeta().get("Profile - Seniority level:"));
    }

    @Test
    void testReadTimesheetTaskData(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("test-tasks.xlsx");

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Timesheet");

            // Create minimal meta fields
            createMetaRow(sheet, 0, "Specific Contract Reference:", "TEST");

            // Create header row
            Row headerRow = sheet.createRow(5);
            headerRow.createCell(1).setCellValue("Day");
            headerRow.createCell(2).setCellValue("Tasks");
            headerRow.createCell(3).setCellValue("Hours");

            // Create data rows
            createDataRow(sheet, 6, 1, "Development", 8.0);
            createDataRow(sheet, 7, 2, "Testing", 7.5);
            createDataRow(sheet, 8, 3, "Code Review", 6.0);

            try (FileOutputStream fos = new FileOutputStream(testFile.toFile())) {
                wb.write(fos);
            }
        }

        HhmmssDto result = XlsService.readTimesheet(testFile);

        assertNotNull(result);
        assertEquals(3, result.getTasks().size());

        DayData day1 = result.getTasks().get(1);
        assertNotNull(day1);
        assertEquals("Development", day1.getTask());
        assertEquals(8.0, day1.getHoursFlexibilityPeriod());

        DayData day2 = result.getTasks().get(2);
        assertNotNull(day2);
        assertEquals("Testing", day2.getTask());
        assertEquals(7.5, day2.getHoursFlexibilityPeriod());

        DayData day3 = result.getTasks().get(3);
        assertNotNull(day3);
        assertEquals("Code Review", day3.getTask());
        assertEquals(6.0, day3.getHoursFlexibilityPeriod());
    }

    @Test
    void testReadTimesheetWithMissingSheet(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("test-no-sheet.xlsx");

        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("WrongSheetName");

            try (FileOutputStream fos = new FileOutputStream(testFile.toFile())) {
                wb.write(fos);
            }
        }

        // The method throws IllegalStateException when "Timesheet" sheet is not found
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> XlsService.readTimesheet(testFile));

        assertTrue(exception.getMessage().contains("Sheet 'Timesheet' not found"));
    }

    @Test
    void testReadTimesheetWithNonExistentFile() {
        Path nonExistentFile = Path.of("non-existent-file.xlsx");

        // Should not throw, but return empty result
        HhmmssDto result = XlsService.readTimesheet(nonExistentFile);

        assertNotNull(result);
        // The method logs error but returns a DTO
    }

    @Test
    void testReadTimesheetWithFormulaCells(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("test-formula.xlsx");

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Timesheet");

            // Create minimal meta field
            createMetaRow(sheet, 0, "Specific Contract Reference:", "TEST");

            // Create header row
            Row headerRow = sheet.createRow(5);
            headerRow.createCell(1).setCellValue("Day");
            headerRow.createCell(2).setCellValue("Tasks");
            headerRow.createCell(3).setCellValue("Hours");

            // Create data row with formula
            Row dataRow = sheet.createRow(6);
            dataRow.createCell(1).setCellValue(1.0);
            dataRow.createCell(2).setCellValue("Development");
            Cell formulaCell = dataRow.createCell(3);
            formulaCell.setCellFormula("4+4"); // Should evaluate to 8

            try (FileOutputStream fos = new FileOutputStream(testFile.toFile())) {
                wb.write(fos);
            }
        }

        HhmmssDto result = XlsService.readTimesheet(testFile);

        assertNotNull(result);
        DayData day1 = result.getTasks().get(1);
        assertNotNull(day1);
        assertEquals("Development", day1.getTask());
        assertEquals(8.0, day1.getHoursFlexibilityPeriod());
    }

    @Test
    void testReadTimesheetWithEmptyTaskName(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("test-empty-task.xlsx");

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Timesheet");

            createMetaRow(sheet, 0, "Specific Contract Reference:", "TEST");

            Row headerRow = sheet.createRow(5);
            headerRow.createCell(1).setCellValue("Day");
            headerRow.createCell(2).setCellValue("Tasks");
            headerRow.createCell(3).setCellValue("Hours");

            // Create data row with empty task
            Row dataRow = sheet.createRow(6);
            dataRow.createCell(1).setCellValue(1.0);
            dataRow.createCell(2).setCellValue("");
            dataRow.createCell(3).setCellValue(8.0);

            try (FileOutputStream fos = new FileOutputStream(testFile.toFile())) {
                wb.write(fos);
            }
        }

        HhmmssDto result = XlsService.readTimesheet(testFile);

        assertNotNull(result);
        DayData day1 = result.getTasks().get(1);
        assertNotNull(day1);
        assertEquals("", day1.getTask());
        assertEquals(8.0, day1.getHoursFlexibilityPeriod());
    }

    @Test
    void testReadTimesheetWithZeroHours(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("test-zero-hours.xlsx");

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Timesheet");

            createMetaRow(sheet, 0, "Specific Contract Reference:", "TEST");

            Row headerRow = sheet.createRow(5);
            headerRow.createCell(1).setCellValue("Day");
            headerRow.createCell(2).setCellValue("Tasks");
            headerRow.createCell(3).setCellValue("Hours");

            // Create data row with zero hours
            Row dataRow = sheet.createRow(6);
            dataRow.createCell(1).setCellValue(1.0);
            dataRow.createCell(2).setCellValue("Holiday");
            dataRow.createCell(3).setCellValue(0.0);

            try (FileOutputStream fos = new FileOutputStream(testFile.toFile())) {
                wb.write(fos);
            }
        }

        HhmmssDto result = XlsService.readTimesheet(testFile);

        assertNotNull(result);
        DayData day1 = result.getTasks().get(1);
        assertNotNull(day1);
        assertEquals("Holiday", day1.getTask());
        assertEquals(0.0, day1.getHoursFlexibilityPeriod());
    }

    @Test
    void testReadTimesheetWithMissingMetaValues(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("test-missing-meta.xlsx");

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Timesheet");

            // Create meta row without value
            Row metaRow = sheet.createRow(0);
            metaRow.createCell(1).setCellValue("Specific Contract Reference:");
            // No value cell created

            Row headerRow = sheet.createRow(5);
            headerRow.createCell(1).setCellValue("Day");

            try (FileOutputStream fos = new FileOutputStream(testFile.toFile())) {
                wb.write(fos);
            }
        }

        HhmmssDto result = XlsService.readTimesheet(testFile);

        assertNotNull(result);
        String contractRef = result.getMeta().get("Specific Contract Reference:");
        // Should return empty string for missing values
        assertEquals("", contractRef);
    }

    @Test
    void testUpdatePeriodInExcelFile(@TempDir Path tempDir) throws IOException, InvalidFormatException {
        Path testFile = tempDir.resolve("test-update-period.xlsx");

        // Create test file with period field
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Timesheet");

            // Create meta fields including period
            createMetaRow(sheet, 0, "Specific Contract Reference:", "TEST");
            createMetaRow(sheet, 1, "Period (month/year):", "12/2024");

            // Create header row
            Row headerRow = sheet.createRow(10);
            headerRow.createCell(1).setCellValue("Day");
            headerRow.createCell(2).setCellValue("Tasks");
            headerRow.createCell(3).setCellValue("Hours");

            // Create data rows for all 31 days
            for (int day = 1; day <= 31; day++) {
                createDataRow(sheet, 10 + day, day, "Work", 8.0);
            }

            try (FileOutputStream fos = new FileOutputStream(testFile.toFile())) {
                wb.write(fos);
            }
        }

        // Update period to February 2024 (29 days - leap year)
        xlsService.updatePeriod(testFile, "02/2024");

        // Read back and verify
        try (Workbook wb = new XSSFWorkbook(testFile.toFile())) {
            Sheet sheet = wb.getSheet("Timesheet");
            assertNotNull(sheet);

            // Verify period was updated
            Row periodRow = sheet.getRow(1);
            String updatedPeriod = periodRow.getCell(2).getStringCellValue();
            assertEquals("02/2024", updatedPeriod);

            // Verify days 30 and 31 are cleared
            Row day30Row = sheet.getRow(40); // headerRow(10) + day(30)
            Row day31Row = sheet.getRow(41); // headerRow(10) + day(31)

            // Day cell should be blank
            assertTrue(day30Row.getCell(XlsService.COL_DAY) == null || day30Row.getCell(XlsService.COL_DAY).getCellType() == org.apache.poi.ss.usermodel.CellType.BLANK);
            assertTrue(day31Row.getCell(XlsService.COL_DAY) == null || day31Row.getCell(XlsService.COL_DAY).getCellType() == org.apache.poi.ss.usermodel.CellType.BLANK);

            // Task cell should be blank
            assertTrue(day30Row.getCell(XlsService.COL_TASK) == null || day30Row.getCell(XlsService.COL_TASK).getCellType() == org.apache.poi.ss.usermodel.CellType.BLANK);
            assertTrue(day31Row.getCell(XlsService.COL_TASK) == null || day31Row.getCell(XlsService.COL_TASK).getCellType() == org.apache.poi.ss.usermodel.CellType.BLANK);

            // All hours cells should be blank
            for (int col = XlsService.COL_HOURS_FLEXIBILITY; col <= XlsService.COL_HOURS_NON_INVOICEABLE; col++) {
                assertTrue(day30Row.getCell(col) == null || day30Row.getCell(col).getCellType() == org.apache.poi.ss.usermodel.CellType.BLANK,
                        "Day 30 column " + col + " should be blank");
                assertTrue(day31Row.getCell(col) == null || day31Row.getCell(col).getCellType() == org.apache.poi.ss.usermodel.CellType.BLANK,
                        "Day 31 column " + col + " should be blank");
            }

            // Verify day 29 still exists (leap year)
            Row day29Row = sheet.getRow(39);
            assertEquals(29.0, day29Row.getCell(XlsService.COL_DAY).getNumericCellValue());
        }
    }

    @Test
    void testUpdatePeriodFor30DayMonth(@TempDir Path tempDir) throws IOException, InvalidFormatException {
        Path testFile = tempDir.resolve("test-update-period-30days.xlsx");

        // Create test file
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Timesheet");

            createMetaRow(sheet, 0, "Period (month/year):", "01/2024");

            Row headerRow = sheet.createRow(5);
            headerRow.createCell(1).setCellValue("Day");
            headerRow.createCell(2).setCellValue("Tasks");
            headerRow.createCell(3).setCellValue("Hours");

            for (int day = 1; day <= 31; day++) {
                createDataRow(sheet, 5 + day, day, "Work", 8.0);
            }

            try (FileOutputStream fos = new FileOutputStream(testFile.toFile())) {
                wb.write(fos);
            }
        }

        // Update to April (30 days)
        xlsService.updatePeriod(testFile, "04/2024");

        // Verify day 31 is cleared
        try (Workbook wb = new XSSFWorkbook(testFile.toFile())) {
            Sheet sheet = wb.getSheet("Timesheet");
            Row day31Row = sheet.getRow(36); // headerRow(5) + day(31)

            assertTrue(day31Row.getCell(XlsService.COL_DAY) == null || day31Row.getCell(XlsService.COL_DAY).getCellType() == org.apache.poi.ss.usermodel.CellType.BLANK);

            // Day 30 should still exist
            Row day30Row = sheet.getRow(35);
            assertEquals(30.0, day30Row.getCell(XlsService.COL_DAY).getNumericCellValue());
        }
    }

    @Test
    void testUpdatePeriodForFebruaryNonLeapYear(@TempDir Path tempDir) throws IOException, InvalidFormatException {
        Path testFile = tempDir.resolve("test-update-period-feb.xlsx");

        // Create test file
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Timesheet");

            createMetaRow(sheet, 0, "Period (month/year):", "01/2024");

            Row headerRow = sheet.createRow(5);
            headerRow.createCell(1).setCellValue("Day");
            headerRow.createCell(2).setCellValue("Tasks");
            headerRow.createCell(3).setCellValue("Hours");

            for (int day = 1; day <= 31; day++) {
                createDataRow(sheet, 5 + day, day, "Work", 8.0);
            }

            try (FileOutputStream fos = new FileOutputStream(testFile.toFile())) {
                wb.write(fos);
            }
        }

        // Update to February 2025 (28 days - non-leap year)
        xlsService.updatePeriod(testFile, "02/2025");

        // Verify days 29-31 are cleared
        try (Workbook wb = new XSSFWorkbook(testFile.toFile())) {
            Sheet sheet = wb.getSheet("Timesheet");

            for (int day = 29; day <= 31; day++) {
                Row dayRow = sheet.getRow(5 + day);
                assertTrue(dayRow.getCell(XlsService.COL_DAY) == null || dayRow.getCell(XlsService.COL_DAY).getCellType() == org.apache.poi.ss.usermodel.CellType.BLANK,
                        "Day " + day + " should be cleared");
            }

            // Day 28 should still exist
            Row day28Row = sheet.getRow(33);
            assertEquals(28.0, day28Row.getCell(XlsService.COL_DAY).getNumericCellValue());
        }
    }

    @Test
    void testUpdatePeriodWithMissingPeriodField(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("test-no-period-field.xlsx");

        // Create file without period field
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Timesheet");

            createMetaRow(sheet, 0, "Specific Contract Reference:", "TEST");

            Row headerRow = sheet.createRow(5);
            headerRow.createCell(1).setCellValue("Day");

            try (FileOutputStream fos = new FileOutputStream(testFile.toFile())) {
                wb.write(fos);
            }
        }

        // Should not throw, just log warning
        assertDoesNotThrow(() -> xlsService.updatePeriod(testFile, "01/2025"));
    }

    @Test
    void testHighlightWeekendsAndHolidaysInFile(@TempDir Path tempDir) throws IOException, InvalidFormatException {
        Path testFile = tempDir.resolve("test-highlight.xlsx");

        // Create test file for January 2025
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Timesheet");

            createMetaRow(sheet, 0, "Period (month/year):", "01/2025");

            Row headerRow = sheet.createRow(5);
            headerRow.createCell(1).setCellValue("Day");
            headerRow.createCell(2).setCellValue("Tasks");
            headerRow.createCell(3).setCellValue("Hours");

            // Create data rows for January 2025 (31 days)
            for (int day = 1; day <= 31; day++) {
                createDataRow(sheet, 5 + day, day, "Work", 8.0);
            }

            try (FileOutputStream fos = new FileOutputStream(testFile.toFile())) {
                wb.write(fos);
            }
        }

        // Apply highlighting
        xlsService.highlightWeekendsAndHolidaysInFile(testFile);

        // Verify highlighting was applied
        try (Workbook wb = new XSSFWorkbook(testFile.toFile())) {
            Sheet sheet = wb.getSheet("Timesheet");
            assertNotNull(sheet);

            // January 1, 2025 is Wednesday (New Year's Day - should be highlighted as holiday)
            Row day1Row = sheet.getRow(6); // headerRow(5) + day(1)
            Cell day1Cell = day1Row.getCell(XlsService.COL_DAY);
            assertEquals(IndexedColors.YELLOW.getIndex(), day1Cell.getCellStyle().getFillForegroundColor());

            // January 4, 2025 is Saturday (should be highlighted as weekend)
            Row day4Row = sheet.getRow(9); // headerRow(5) + day(4)
            Cell day4Cell = day4Row.getCell(XlsService.COL_DAY);
            assertEquals(IndexedColors.YELLOW.getIndex(), day4Cell.getCellStyle().getFillForegroundColor());

            // January 5, 2025 is Sunday (should be highlighted as weekend)
            Row day5Row = sheet.getRow(10); // headerRow(5) + day(5)
            Cell day5Cell = day5Row.getCell(XlsService.COL_DAY);
            assertEquals(IndexedColors.YELLOW.getIndex(), day5Cell.getCellStyle().getFillForegroundColor());

            // January 6, 2025 is Monday (regular weekday - should not be highlighted)
            Row day6Row = sheet.getRow(11); // headerRow(5) + day(6)
            Cell day6Cell = day6Row.getCell(XlsService.COL_DAY);
            assertNotEquals(IndexedColors.YELLOW.getIndex(), day6Cell.getCellStyle().getFillForegroundColor());
        }
    }

    @Test
    void testHighlightWeekendsInFebruary2024(@TempDir Path tempDir) throws IOException, InvalidFormatException {
        Path testFile = tempDir.resolve("test-highlight-feb.xlsx");

        // Create test file for February 2024
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Timesheet");

            createMetaRow(sheet, 0, "Period (month/year):", "02/2024");

            Row headerRow = sheet.createRow(5);
            headerRow.createCell(1).setCellValue("Day");
            headerRow.createCell(2).setCellValue("Tasks");

            // Create data rows for February 2024 (29 days - leap year)
            for (int day = 1; day <= 29; day++) {
                createDataRow(sheet, 5 + day, day, "Work", 8.0);
            }

            try (FileOutputStream fos = new FileOutputStream(testFile.toFile())) {
                wb.write(fos);
            }
        }

        // Apply highlighting
        xlsService.highlightWeekendsAndHolidaysInFile(testFile);

        // Verify highlighting
        try (Workbook wb = new XSSFWorkbook(testFile.toFile())) {
            Sheet sheet = wb.getSheet("Timesheet");

            // February 3, 2024 is Saturday
            Row day3Row = sheet.getRow(8);
            Cell day3Cell = day3Row.getCell(XlsService.COL_DAY);
            assertEquals(IndexedColors.YELLOW.getIndex(), day3Cell.getCellStyle().getFillForegroundColor());

            // February 4, 2024 is Sunday
            Row day4Row = sheet.getRow(9);
            Cell day4Cell = day4Row.getCell(XlsService.COL_DAY);
            assertEquals(IndexedColors.YELLOW.getIndex(), day4Cell.getCellStyle().getFillForegroundColor());

            // February 5, 2024 is Monday (weekday)
            Row day5Row = sheet.getRow(10);
            Cell day5Cell = day5Row.getCell(XlsService.COL_DAY);
            assertNotEquals(IndexedColors.YELLOW.getIndex(), day5Cell.getCellStyle().getFillForegroundColor());
        }
    }

    @Test
    void testUpdatePeriodAlsoHighlights(@TempDir Path tempDir) throws IOException, InvalidFormatException {
        Path testFile = tempDir.resolve("test-update-and-highlight.xlsx");

        // Create test file
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Timesheet");

            createMetaRow(sheet, 0, "Period (month/year):", "12/2024");

            Row headerRow = sheet.createRow(5);
            headerRow.createCell(1).setCellValue("Day");
            headerRow.createCell(2).setCellValue("Tasks");

            for (int day = 1; day <= 31; day++) {
                createDataRow(sheet, 5 + day, day, "Work", 8.0);
            }

            try (FileOutputStream fos = new FileOutputStream(testFile.toFile())) {
                wb.write(fos);
            }
        }

        // Update period to January 2025 (which should also apply highlighting)
        xlsService.updatePeriod(testFile, "01/2025");

        // Verify both period update and highlighting
        try (Workbook wb = new XSSFWorkbook(testFile.toFile())) {
            Sheet sheet = wb.getSheet("Timesheet");

            // Verify period was updated
            Row periodRow = sheet.getRow(0);
            assertEquals("01/2025", periodRow.getCell(2).getStringCellValue());

            // Verify January 1, 2025 (holiday) is highlighted
            Row day1Row = sheet.getRow(6);
            Cell day1Cell = day1Row.getCell(XlsService.COL_DAY);
            assertEquals(IndexedColors.YELLOW.getIndex(), day1Cell.getCellStyle().getFillForegroundColor());

            // Verify a weekend is highlighted - January 4, 2025 is Saturday
            Row day4Row = sheet.getRow(9); // headerRow(5) + day(4)
            Cell day4Cell = day4Row.getCell(XlsService.COL_DAY);
            assertEquals(IndexedColors.YELLOW.getIndex(), day4Cell.getCellStyle().getFillForegroundColor());
        }
    }

    // Helper methods to create test data

    private void createMetaRow(Sheet sheet, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);
        row.createCell(1).setCellValue(label);  // Column B - labels
        row.createCell(2).setCellValue(value);   // Column C - value for meta
    }

    private void createDataRow(Sheet sheet, int rowNum, int day, String task, double hours) {
        Row row = sheet.createRow(rowNum);
        row.createCell(XlsService.COL_DAY).setCellValue((double) day);
        row.createCell(XlsService.COL_TASK).setCellValue(task);
        row.createCell(XlsService.COL_HOURS_FLEXIBILITY).setCellValue(hours);
        row.createCell(XlsService.COL_HOURS_OUTSIDE_FLEXIBILITY).setCellValue(0.0);
        row.createCell(XlsService.COL_HOURS_SATURDAYS).setCellValue(0.0);
        row.createCell(XlsService.COL_HOURS_SUNDAYS_HOLIDAYS).setCellValue(0.0);
        row.createCell(XlsService.COL_HOURS_STANDBY).setCellValue(0.0);
        row.createCell(XlsService.COL_HOURS_NON_INVOICEABLE).setCellValue(0.0);
    }
}
