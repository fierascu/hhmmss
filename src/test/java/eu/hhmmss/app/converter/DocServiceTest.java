package eu.hhmmss.app.converter;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DocServiceTest {

    @Test
    void testAddDataToDocs() {
        Path xlsxFilePath = Path.of("src/test/resources/timesheet-in.xlsx");
        HhmmssDto hhmmssXlsxFormat = XlsService.readTimesheet(xlsxFilePath);
        assertNotNull(hhmmssXlsxFormat);
        assertEquals(30, hhmmssXlsxFormat.getTasks().size());
        assertEquals("work", hhmmssXlsxFormat.getTasks().get(30).getKey());
        assertEquals(8.5d, hhmmssXlsxFormat.getTasks().get(30).getValue());
        assertEquals(6, hhmmssXlsxFormat.getMeta().size());
        assertEquals("contract_no", hhmmssXlsxFormat.getMeta().get("Specific Contract Reference:"));

        Path docxInFilePath = Path.of("src/test/resources/timesheet-out.docx");
        Path docxOutFilePath = Path.of("src/test/resources/timesheet-out-generated.docx");
        Path fileProcessed = DocService.addDataToDocs(docxInFilePath, hhmmssXlsxFormat, docxOutFilePath);

        assertNotNull(fileProcessed);
    }

    @Test
    void testAddDataToDocsWithFebruaryPeriod(@TempDir Path tempDir) throws Exception {
        // Create test data for February 2024 (29 days - leap year)
        HhmmssDto dto = new HhmmssDto();
        Map<String, String> meta = new HashMap<>();
        meta.put("Specific Contract Reference:", "TEST-123");
        meta.put("Purchase Order no (PO):", "PO-456");
        meta.put("Period (month/year):", "02/2024"); // February 2024
        meta.put("First Name of person:", "John");
        meta.put("Family Name of person:", "Doe");
        meta.put("Profile - Seniority level:", "Senior");
        dto.setMeta(meta);

        // Add tasks for all 31 days (to test clearing)
        Map<Integer, ImmutablePair<String, Double>> tasks = new HashMap<>();
        for (int day = 1; day <= 31; day++) {
            tasks.put(day, new ImmutablePair<>("Work", 8.0));
        }
        dto.setTasks(tasks);

        Path templatePath = Path.of("src/test/resources/timesheet-out.docx");
        Path outputPath = tempDir.resolve("test-february.docx");

        // Generate DOCX
        DocService.addDataToDocs(templatePath, dto, outputPath);

        // Verify output file exists
        assertTrue(Files.exists(outputPath));

        // Read and verify the generated DOCX
        try (FileInputStream fis = new FileInputStream(outputPath.toFile());
             XWPFDocument doc = new XWPFDocument(fis)) {

            assertFalse(doc.getTables().isEmpty());
            XWPFTable table = doc.getTables().get(0);

            // Verify days 1-29 have data (February 2024 has 29 days)
            for (int day = 1; day <= 29; day++) {
                XWPFTableRow row = table.getRow(day);
                assertNotNull(row, "Row for day " + day + " should exist");
                String cellText = row.getCell(1).getText();
                assertEquals("Work", cellText, "Day " + day + " should have task data");
            }

            // Verify days 30-31 are cleared
            XWPFTableRow day30Row = table.getRow(30);
            XWPFTableRow day31Row = table.getRow(31);

            assertTrue(day30Row.getCell(1).getText().isEmpty(), "Day 30 should be empty for February");
            assertTrue(day31Row.getCell(1).getText().isEmpty(), "Day 31 should be empty for February");
        }
    }

    @Test
    void testAddDataToDocsWithAprilPeriod(@TempDir Path tempDir) throws Exception {
        // Create test data for April 2024 (30 days)
        HhmmssDto dto = new HhmmssDto();
        Map<String, String> meta = new HashMap<>();
        meta.put("Period (month/year):", "04/2024"); // April 2024
        meta.put("Specific Contract Reference:", "TEST");
        dto.setMeta(meta);

        // Add tasks for all 31 days
        Map<Integer, ImmutablePair<String, Double>> tasks = new HashMap<>();
        for (int day = 1; day <= 31; day++) {
            tasks.put(day, new ImmutablePair<>("Development", 7.5));
        }
        dto.setTasks(tasks);

        Path templatePath = Path.of("src/test/resources/timesheet-out.docx");
        Path outputPath = tempDir.resolve("test-april.docx");

        // Generate DOCX
        DocService.addDataToDocs(templatePath, dto, outputPath);

        // Verify output
        assertTrue(Files.exists(outputPath));

        try (FileInputStream fis = new FileInputStream(outputPath.toFile());
             XWPFDocument doc = new XWPFDocument(fis)) {

            XWPFTable table = doc.getTables().get(0);

            // Day 30 should have data
            XWPFTableRow day30Row = table.getRow(30);
            assertEquals("Development", day30Row.getCell(1).getText());

            // Day 31 should be empty
            XWPFTableRow day31Row = table.getRow(31);
            assertTrue(day31Row.getCell(1).getText().isEmpty(), "Day 31 should be empty for April");
        }
    }

    @Test
    void testAddDataToDocsWithFebruaryNonLeapYear(@TempDir Path tempDir) throws Exception {
        // Create test data for February 2025 (28 days - non-leap year)
        HhmmssDto dto = new HhmmssDto();
        Map<String, String> meta = new HashMap<>();
        meta.put("Period (month/year):", "02/2025"); // February 2025
        meta.put("Specific Contract Reference:", "TEST");
        dto.setMeta(meta);

        // Add tasks for all 31 days
        Map<Integer, ImmutablePair<String, Double>> tasks = new HashMap<>();
        for (int day = 1; day <= 31; day++) {
            tasks.put(day, new ImmutablePair<>("Testing", 6.0));
        }
        dto.setTasks(tasks);

        Path templatePath = Path.of("src/test/resources/timesheet-out.docx");
        Path outputPath = tempDir.resolve("test-february-nonleap.docx");

        // Generate DOCX
        DocService.addDataToDocs(templatePath, dto, outputPath);

        assertTrue(Files.exists(outputPath));

        try (FileInputStream fis = new FileInputStream(outputPath.toFile());
             XWPFDocument doc = new XWPFDocument(fis)) {

            XWPFTable table = doc.getTables().get(0);

            // Day 28 should have data
            XWPFTableRow day28Row = table.getRow(28);
            assertEquals("Testing", day28Row.getCell(1).getText());

            // Days 29-31 should be empty
            for (int day = 29; day <= 31; day++) {
                XWPFTableRow dayRow = table.getRow(day);
                assertTrue(dayRow.getCell(1).getText().isEmpty(),
                        "Day " + day + " should be empty for February (non-leap year)");
            }
        }
    }

    @Test
    void testAddDataToDocsWithInvalidPeriodDefaultsTo31Days(@TempDir Path tempDir) throws Exception {
        // Create test data with invalid period format
        HhmmssDto dto = new HhmmssDto();
        Map<String, String> meta = new HashMap<>();
        meta.put("Period (month/year):", "Invalid"); // Invalid format
        meta.put("Specific Contract Reference:", "TEST");
        dto.setMeta(meta);

        // Add tasks
        Map<Integer, ImmutablePair<String, Double>> tasks = new HashMap<>();
        tasks.put(31, new ImmutablePair<>("Work", 8.0));
        dto.setTasks(tasks);

        Path templatePath = Path.of("src/test/resources/timesheet-out.docx");
        Path outputPath = tempDir.resolve("test-invalid-period.docx");

        // Should not throw, defaults to 31 days
        assertDoesNotThrow(() -> DocService.addDataToDocs(templatePath, dto, outputPath));

        assertTrue(Files.exists(outputPath));

        // Verify day 31 has data (default behavior)
        try (FileInputStream fis = new FileInputStream(outputPath.toFile());
             XWPFDocument doc = new XWPFDocument(fis)) {

            XWPFTable table = doc.getTables().get(0);
            XWPFTableRow day31Row = table.getRow(31);
            assertEquals("Work", day31Row.getCell(1).getText());
        }
    }
}