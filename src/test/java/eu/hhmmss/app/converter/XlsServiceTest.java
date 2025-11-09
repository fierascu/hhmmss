package eu.hhmmss.app.converter;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class XlsServiceTest {

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

        Pair<String, Double> day1 = result.getTasks().get(1);
        assertNotNull(day1);
        assertEquals("Development", day1.getKey());
        assertEquals(8.0, day1.getValue());

        Pair<String, Double> day2 = result.getTasks().get(2);
        assertNotNull(day2);
        assertEquals("Testing", day2.getKey());
        assertEquals(7.5, day2.getValue());

        Pair<String, Double> day3 = result.getTasks().get(3);
        assertNotNull(day3);
        assertEquals("Code Review", day3.getKey());
        assertEquals(6.0, day3.getValue());
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
        Pair<String, Double> day1 = result.getTasks().get(1);
        assertNotNull(day1);
        assertEquals("Development", day1.getKey());
        assertEquals(8.0, day1.getValue());
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
        Pair<String, Double> day1 = result.getTasks().get(1);
        assertNotNull(day1);
        assertEquals("", day1.getKey());
        assertEquals(8.0, day1.getValue());
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
        Pair<String, Double> day1 = result.getTasks().get(1);
        assertNotNull(day1);
        assertEquals("Holiday", day1.getKey());
        assertEquals(0.0, day1.getValue());
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

    // Helper methods to create test data

    private void createMetaRow(Sheet sheet, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);
        row.createCell(1).setCellValue(label);
        row.createCell(2).setCellValue(value);
    }

    private void createDataRow(Sheet sheet, int rowNum, int day, String task, double hours) {
        Row row = sheet.createRow(rowNum);
        row.createCell(1).setCellValue((double) day);
        row.createCell(2).setCellValue(task);
        row.createCell(3).setCellValue(hours);
    }
}
