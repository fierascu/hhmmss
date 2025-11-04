package eu.hhmmss.app.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static eu.hhmmss.app.validation.FileValidationException.ValidationErrorCode.*;
import static org.junit.jupiter.api.Assertions.*;

class TimesheetFileValidatorTest {

    private TimesheetFileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TimesheetFileValidator();
    }

    @Test
    void testValidExcelFile() throws IOException, FileValidationException {
        // Use the actual test file
        Path testFile = Path.of("src/test/resources/timesheet-in.xlsx");
        if (Files.exists(testFile)) {
            byte[] content = Files.readAllBytes(testFile);
            MultipartFile file = new MockMultipartFile(
                    "file",
                    "timesheet.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    content
            );

            assertDoesNotThrow(() -> validator.validateTimesheetFile(file));
        }
    }

    @Test
    void testNullFile() {
        assertThrows(NullPointerException.class, () -> validator.validateTimesheetFile(null));
    }

    @Test
    void testEmptyFile() {
        MultipartFile file = new MockMultipartFile(
                "file",
                "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[0]
        );

        FileValidationException exception = assertThrows(
                FileValidationException.class,
                () -> validator.validateTimesheetFile(file)
        );

        assertEquals(FILE_EMPTY, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("empty"));
    }

    @Test
    void testFileTooSmall() {
        MultipartFile file = new MockMultipartFile(
                "file",
                "tiny.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[50] // Less than MIN_FILE_SIZE
        );

        FileValidationException exception = assertThrows(
                FileValidationException.class,
                () -> validator.validateTimesheetFile(file)
        );

        assertEquals(FILE_EMPTY, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("too small"));
    }

    @Test
    void testFileTooLarge() {
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11 MB > MAX_FILE_SIZE
        MultipartFile file = new MockMultipartFile(
                "file",
                "large.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                largeContent
        );

        FileValidationException exception = assertThrows(
                FileValidationException.class,
                () -> validator.validateTimesheetFile(file)
        );

        assertEquals(FILE_TOO_LARGE, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("exceeds maximum"));
    }

    @Test
    void testInvalidExtension() {
        MultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                new byte[1000]
        );

        FileValidationException exception = assertThrows(
                FileValidationException.class,
                () -> validator.validateTimesheetFile(file)
        );

        assertEquals(INVALID_FILE_TYPE, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Invalid file extension"));
    }

    @Test
    void testMissingFilename() {
        MultipartFile file = new MockMultipartFile(
                "file",
                null,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[1000]
        );

        FileValidationException exception = assertThrows(
                FileValidationException.class,
                () -> validator.validateTimesheetFile(file)
        );

        assertEquals(INVALID_FILE_TYPE, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Filename is missing"));
    }

    @Test
    void testInvalidExcelFormat() {
        // Create a file with .xlsx extension but invalid content
        byte[] invalidContent = "This is not an Excel file".getBytes();
        MultipartFile file = new MockMultipartFile(
                "file",
                "fake.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                invalidContent
        );

        FileValidationException exception = assertThrows(
                FileValidationException.class,
                () -> validator.validateTimesheetFile(file)
        );

        assertTrue(
                exception.getErrorCode() == INVALID_EXCEL_FORMAT ||
                exception.getErrorCode() == CORRUPT_FILE
        );
    }

    @Test
    void testMissingRequiredSheet() throws IOException {
        // Create a minimal valid Excel file without the required "Timesheet" sheet
        byte[] excelWithoutTimesheetSheet = createMinimalExcelWithoutTimesheetSheet();

        MultipartFile file = new MockMultipartFile(
                "file",
                "no-timesheet-sheet.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelWithoutTimesheetSheet
        );

        FileValidationException exception = assertThrows(
                FileValidationException.class,
                () -> validator.validateTimesheetFile(file)
        );

        assertEquals(MISSING_REQUIRED_SHEET, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Timesheet"));
    }

    @Test
    void testGetMaxFileSize() {
        long maxSize = validator.getMaxFileSize();
        assertEquals(10 * 1024 * 1024, maxSize); // 10 MB
    }

    @Test
    void testXlsExtensionAllowed() {
        MultipartFile file = new MockMultipartFile(
                "file",
                "timesheet.xls",
                "application/vnd.ms-excel",
                new byte[1000]
        );

        // Should pass extension validation (will fail on Excel format validation)
        FileValidationException exception = assertThrows(
                FileValidationException.class,
                () -> validator.validateTimesheetFile(file)
        );

        // Should not fail on extension check
        assertNotEquals(INVALID_FILE_TYPE, exception.getErrorCode());
    }

    /**
     * Helper method to create a minimal Excel file without "Timesheet" sheet.
     * Creates a ZIP file (which is the Excel format) with minimal structure.
     */
    private byte[] createMinimalExcelWithoutTimesheetSheet() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add minimal Excel structure
            // _rels/.rels
            zos.putNextEntry(new ZipEntry("_rels/.rels"));
            String rels = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                    "</Relationships>";
            zos.write(rels.getBytes());
            zos.closeEntry();

            // [Content_Types].xml
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            String contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                    "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                    "</Types>";
            zos.write(contentTypes.getBytes());
            zos.closeEntry();

            // xl/workbook.xml with a sheet named "Sheet1" (NOT "Timesheet")
            zos.putNextEntry(new ZipEntry("xl/workbook.xml"));
            String workbook = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                    "<sheets>" +
                    "<sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/>" +
                    "</sheets>" +
                    "</workbook>";
            zos.write(workbook.getBytes());
            zos.closeEntry();

            // xl/_rels/workbook.xml.rels
            zos.putNextEntry(new ZipEntry("xl/_rels/workbook.xml.rels"));
            String workbookRels = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                    "</Relationships>";
            zos.write(workbookRels.getBytes());
            zos.closeEntry();

            // xl/worksheets/sheet1.xml
            zos.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            String worksheet = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                    "<sheetData/>" +
                    "</worksheet>";
            zos.write(worksheet.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
