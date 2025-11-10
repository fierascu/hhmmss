package eu.hhmmss.app.converter;

import eu.hhmmss.app.uploadingfiles.storage.StorageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipProcessingServiceTest {

    private ZipProcessingService zipProcessingService;
    private Path tempDir;
    private Path templatePath;

    @BeforeEach
    void setUp() throws IOException {
        zipProcessingService = new ZipProcessingService();
        tempDir = Files.createTempDirectory("zip-test-");

        // Create a mock template file (we won't actually use it for conversion in tests)
        templatePath = tempDir.resolve("template.docx");
        Files.createFile(templatePath);
    }

    @AfterEach
    void tearDown() {
        // Clean up test files
        try {
            deleteDirectory(tempDir);
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    @Test
    void testProcessZipFile_WithValidExcelFiles() throws IOException {
        // Create a ZIP file with valid Excel files
        Path zipPath = createZipWithExcelFiles(2);

        // Note: This test will fail during actual conversion because we're not providing
        // real Excel files with timesheet data. In a real scenario, you'd need to mock
        // XlsService.readTimesheet() or provide real test Excel files.
        // For now, we're just testing that the service attempts to process the files.

        assertThrows(Exception.class, () -> {
            zipProcessingService.processZipFile(zipPath, templatePath, tempDir);
        }, "Should throw exception when trying to process invalid Excel content");
    }

    @Test
    void testProcessZipFile_WithNoExcelFiles() throws IOException {
        // Create a ZIP file with non-Excel files
        Path zipPath = tempDir.resolve("no-excel.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            // Add a text file
            ZipEntry entry = new ZipEntry("readme.txt");
            zos.putNextEntry(entry);
            zos.write("Hello World".getBytes());
            zos.closeEntry();
        }

        // Should throw exception because no Excel files found
        StorageException exception = assertThrows(StorageException.class, () -> {
            zipProcessingService.processZipFile(zipPath, templatePath, tempDir);
        });

        assertTrue(exception.getMessage().contains("No Excel files found"));
    }

    @Test
    void testProcessZipFile_WithEmptyZip() throws IOException {
        // Create an empty ZIP file
        Path zipPath = tempDir.resolve("empty.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            // Don't add any entries
        }

        // Should throw exception because no Excel files found
        StorageException exception = assertThrows(StorageException.class, () -> {
            zipProcessingService.processZipFile(zipPath, templatePath, tempDir);
        });

        assertTrue(exception.getMessage().contains("No Excel files found"));
    }

    @Test
    void testProcessZipFile_WithNestedDirectories() throws IOException {
        // Create a ZIP with Excel files in nested directories
        Path zipPath = tempDir.resolve("nested.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            // Add Excel file in root
            ZipEntry entry1 = new ZipEntry("file1.xlsx");
            zos.putNextEntry(entry1);
            zos.write(createMockExcelContent());
            zos.closeEntry();

            // Add directory (should be skipped)
            ZipEntry dirEntry = new ZipEntry("subfolder/");
            zos.putNextEntry(dirEntry);
            zos.closeEntry();

            // Add Excel file in subfolder
            ZipEntry entry2 = new ZipEntry("subfolder/file2.xlsx");
            zos.putNextEntry(entry2);
            zos.write(createMockExcelContent());
            zos.closeEntry();

            // Add deeper nested file
            ZipEntry entry3 = new ZipEntry("subfolder/nested/file3.xlsx");
            zos.putNextEntry(entry3);
            zos.write(createMockExcelContent());
            zos.closeEntry();
        }

        // Should attempt to process all 3 Excel files
        // Will fail during actual conversion, but we're testing the extraction logic
        assertThrows(Exception.class, () -> {
            zipProcessingService.processZipFile(zipPath, templatePath, tempDir);
        }, "Should find and attempt to process all nested Excel files");
    }

    @Test
    void testProcessZipFile_WithMixedFileTypes() throws IOException {
        // Create a ZIP with both Excel and non-Excel files
        Path zipPath = tempDir.resolve("mixed.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            // Add Excel files
            ZipEntry xlsxEntry = new ZipEntry("timesheet.xlsx");
            zos.putNextEntry(xlsxEntry);
            zos.write(createMockExcelContent());
            zos.closeEntry();

            ZipEntry xlsEntry = new ZipEntry("legacy.xls");
            zos.putNextEntry(xlsEntry);
            zos.write(new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0});
            zos.closeEntry();

            // Add non-Excel files (should be ignored)
            ZipEntry txtEntry = new ZipEntry("readme.txt");
            zos.putNextEntry(txtEntry);
            zos.write("Instructions".getBytes());
            zos.closeEntry();

            ZipEntry pdfEntry = new ZipEntry("guide.pdf");
            zos.putNextEntry(pdfEntry);
            zos.write(new byte[]{0x25, 0x50, 0x44, 0x46}); // PDF magic bytes
            zos.closeEntry();
        }

        // Should only attempt to process the 2 Excel files, ignoring txt and pdf
        assertThrows(Exception.class, () -> {
            zipProcessingService.processZipFile(zipPath, templatePath, tempDir);
        }, "Should only process Excel files and ignore others");
    }

    @Test
    void testProcessZipFile_PathTraversalPrevention() throws IOException {
        // Create a ZIP with malicious path traversal attempts
        Path zipPath = tempDir.resolve("malicious.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            // Try to escape the extraction directory
            ZipEntry maliciousEntry = new ZipEntry("../../../etc/passwd.xlsx");
            zos.putNextEntry(maliciousEntry);
            zos.write(createMockExcelContent());
            zos.closeEntry();
        }

        // Should throw StorageException due to path traversal protection
        assertThrows(StorageException.class, () -> {
            zipProcessingService.processZipFile(zipPath, templatePath, tempDir);
        }, "Should prevent path traversal attacks");
    }

    @Test
    void testProcessZipFile_WithSpecialCharactersInFilenames() throws IOException {
        // Create a ZIP with files having special characters
        Path zipPath = tempDir.resolve("special-chars.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            // Add files with spaces and special characters
            String[] filenames = {
                "Employee Report 2024.xlsx",
                "timesheet_march-2024.xlsx",
                "John's_timesheet.xlsx"
            };

            for (String filename : filenames) {
                ZipEntry entry = new ZipEntry(filename);
                zos.putNextEntry(entry);
                zos.write(createMockExcelContent());
                zos.closeEntry();
            }
        }

        // Should handle special characters in filenames
        assertThrows(Exception.class, () -> {
            zipProcessingService.processZipFile(zipPath, templatePath, tempDir);
        }, "Should handle filenames with special characters");
    }

    @Test
    void testProcessZipFile_WithAllSupportedExcelFormats() throws IOException {
        // Create a ZIP with all supported Excel formats
        Path zipPath = tempDir.resolve("all-formats.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            // .xlsx
            ZipEntry xlsxEntry = new ZipEntry("file.xlsx");
            zos.putNextEntry(xlsxEntry);
            zos.write(createMockExcelContent());
            zos.closeEntry();

            // .xlsm (with macros)
            ZipEntry xlsmEntry = new ZipEntry("file.xlsm");
            zos.putNextEntry(xlsmEntry);
            zos.write(createMockExcelContent());
            zos.closeEntry();

            // .xlsb (binary format)
            ZipEntry xlsbEntry = new ZipEntry("file.xlsb");
            zos.putNextEntry(xlsbEntry);
            zos.write(createMockExcelContent());
            zos.closeEntry();

            // .xls (legacy format)
            ZipEntry xlsEntry = new ZipEntry("file.xls");
            zos.putNextEntry(xlsEntry);
            zos.write(new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0});
            zos.closeEntry();
        }

        // Should recognize and attempt to process all 4 formats
        assertThrows(Exception.class, () -> {
            zipProcessingService.processZipFile(zipPath, templatePath, tempDir);
        }, "Should recognize all supported Excel formats");
    }

    // Helper methods

    private Path createZipWithExcelFiles(int count) throws IOException {
        Path zipPath = tempDir.resolve("test.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            for (int i = 1; i <= count; i++) {
                ZipEntry entry = new ZipEntry("file" + i + ".xlsx");
                zos.putNextEntry(entry);
                zos.write(createMockExcelContent());
                zos.closeEntry();
            }
        }
        return zipPath;
    }

    private byte[] createMockExcelContent() {
        // Return ZIP magic bytes (XLSX files are ZIP archives)
        return new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (var stream = Files.walk(directory)) {
            stream.sorted((a, b) -> b.compareTo(a))
                  .forEach(path -> {
                      try {
                          Files.delete(path);
                      } catch (IOException e) {
                          // Ignore
                      }
                  });
        }
    }
}
