package eu.hhmmss.app.uploadingfiles.storage;

import eu.hhmmss.app.converter.XlsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

class FileCleanupServiceTest {

    private FileCleanupService cleanupService;
    private XlsService xlsService;
    private Path testUploadLocation;

    @BeforeEach
    void setUp() throws IOException {
        // Create mock XlsService
        xlsService = mock(XlsService.class);
        doNothing().when(xlsService).updatePeriod(any(Path.class), anyString());

        // Create service with mock
        cleanupService = new FileCleanupService(xlsService);

        // Set retention period to 7 days for testing
        ReflectionTestUtils.setField(cleanupService, "retentionDays", 7);

        // Initialize the service
        cleanupService.initialize();

        // Get the actual upload location used by the service
        String tempDir = System.getProperty("java.io.tmpdir");
        testUploadLocation = Paths.get(tempDir, "uploads");

        // Ensure the directory exists
        Files.createDirectories(testUploadLocation);
    }

    @AfterEach
    void tearDown() {
        // Clean up test files
        try {
            if (Files.exists(testUploadLocation)) {
                try (Stream<Path> files = Files.list(testUploadLocation)) {
                    for (Path file : files.toList()) {
                        Files.deleteIfExists(file);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    void testInitialize() {
        assertTrue(Files.exists(testUploadLocation), "Upload directory should exist");
        assertTrue(Files.isDirectory(testUploadLocation), "Upload location should be a directory");
    }

    @Test
    void testCleanupDeletesOldFiles() throws IOException {
        // Create an old file (10 days old)
        Path oldFile = testUploadLocation.resolve("old-file.xlsx");
        Files.createFile(oldFile);
        Instant oldTime = Instant.now().minus(10, ChronoUnit.DAYS);
        Files.setLastModifiedTime(oldFile, FileTime.from(oldTime));

        // Run cleanup
        cleanupService.cleanupOldFiles();

        // Verify the old file was deleted
        assertFalse(Files.exists(oldFile), "Old file should be deleted");
    }

    @Test
    void testCleanupRetainsRecentFiles() throws IOException {
        // Create a recent file (2 days old)
        Path recentFile = testUploadLocation.resolve("recent-file.xlsx");
        Files.createFile(recentFile);
        Instant recentTime = Instant.now().minus(2, ChronoUnit.DAYS);
        Files.setLastModifiedTime(recentFile, FileTime.from(recentTime));

        // Run cleanup
        cleanupService.cleanupOldFiles();

        // Verify the recent file was retained
        assertTrue(Files.exists(recentFile), "Recent file should be retained");
    }

    @Test
    void testCleanupMixedFiles() throws IOException {
        // Create multiple files with different ages
        Path oldFile1 = testUploadLocation.resolve("old-file-1.xlsx");
        Path oldFile2 = testUploadLocation.resolve("old-file-2.pdf");
        Path recentFile1 = testUploadLocation.resolve("recent-file-1.docx");
        Path recentFile2 = testUploadLocation.resolve("recent-file-2.zip");

        Files.createFile(oldFile1);
        Files.createFile(oldFile2);
        Files.createFile(recentFile1);
        Files.createFile(recentFile2);

        // Set timestamps
        Instant oldTime = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant recentTime = Instant.now().minus(2, ChronoUnit.DAYS);

        Files.setLastModifiedTime(oldFile1, FileTime.from(oldTime));
        Files.setLastModifiedTime(oldFile2, FileTime.from(oldTime));
        Files.setLastModifiedTime(recentFile1, FileTime.from(recentTime));
        Files.setLastModifiedTime(recentFile2, FileTime.from(recentTime));

        // Run cleanup
        cleanupService.cleanupOldFiles();

        // Verify cleanup results
        assertFalse(Files.exists(oldFile1), "Old file 1 should be deleted");
        assertFalse(Files.exists(oldFile2), "Old file 2 should be deleted");
        assertTrue(Files.exists(recentFile1), "Recent file 1 should be retained");
        assertTrue(Files.exists(recentFile2), "Recent file 2 should be retained");
    }

    @Test
    void testCleanupWithExactRetentionBoundary() throws IOException {
        // Create a file exactly 7 days old (at the boundary)
        Path boundaryFile = testUploadLocation.resolve("boundary-file.xlsx");
        Files.createFile(boundaryFile);
        Instant boundaryTime = Instant.now().minus(7, ChronoUnit.DAYS);
        Files.setLastModifiedTime(boundaryFile, FileTime.from(boundaryTime));

        // Create a file just over 7 days old
        Path overBoundaryFile = testUploadLocation.resolve("over-boundary-file.xlsx");
        Files.createFile(overBoundaryFile);
        Instant overBoundaryTime = Instant.now().minus(7, ChronoUnit.DAYS).minus(1, ChronoUnit.HOURS);
        Files.setLastModifiedTime(overBoundaryFile, FileTime.from(overBoundaryTime));

        // Run cleanup
        cleanupService.cleanupOldFiles();

        // Files older than retention period should be deleted
        assertFalse(Files.exists(overBoundaryFile), "File over 7 days old should be deleted");
    }

    @Test
    void testCleanupWithEmptyDirectory() throws IOException {
        // Ensure directory is empty
        try (Stream<Path> files = Files.list(testUploadLocation)) {
            assertEquals(0, files.count(), "Directory should be empty");
        }

        // Run cleanup - should complete without errors
        assertDoesNotThrow(() -> cleanupService.cleanupOldFiles());
    }

    @Test
    void testCleanupWithNonExistentDirectory() throws IOException {
        // Create a separate service instance with a non-existent directory
        FileCleanupService testService = new FileCleanupService(xlsService);
        ReflectionTestUtils.setField(testService, "retentionDays", 7);

        // Set a non-existent upload location
        Path nonExistentPath = Paths.get(System.getProperty("java.io.tmpdir"), "uploads-nonexistent-test-" + System.currentTimeMillis());
        ReflectionTestUtils.setField(testService, "uploadLocation", nonExistentPath);

        // Run cleanup - should handle gracefully without throwing exception
        assertDoesNotThrow(() -> testService.cleanupOldFiles());

        // Verify the directory was not created
        assertFalse(Files.exists(nonExistentPath), "Non-existent directory should remain non-existent");
    }

    @Test
    void testCleanupIgnoresSubdirectories() throws IOException {
        // Create a subdirectory
        Path subdir = testUploadLocation.resolve("subdir");
        Files.createDirectory(subdir);
        Instant oldTime = Instant.now().minus(10, ChronoUnit.DAYS);
        Files.setLastModifiedTime(subdir, FileTime.from(oldTime));

        // Run cleanup
        cleanupService.cleanupOldFiles();

        // Subdirectory should still exist (we only delete files, not directories)
        assertTrue(Files.exists(subdir), "Subdirectory should be ignored by cleanup");
    }

    @Test
    void testManualCleanup() throws IOException {
        // Create an old file
        Path oldFile = testUploadLocation.resolve("old-manual-file.xlsx");
        Files.createFile(oldFile);
        Instant oldTime = Instant.now().minus(10, ChronoUnit.DAYS);
        Files.setLastModifiedTime(oldFile, FileTime.from(oldTime));

        // Run manual cleanup
        assertDoesNotThrow(() -> cleanupService.manualCleanup());

        // Verify the old file was deleted
        assertFalse(Files.exists(oldFile), "Old file should be deleted by manual cleanup");
    }

    @Test
    void testCleanupWithDifferentRetentionPeriod() throws IOException {
        // Set retention period to 3 days
        ReflectionTestUtils.setField(cleanupService, "retentionDays", 3);
        cleanupService.initialize();

        // Create a file 5 days old
        Path oldFile = testUploadLocation.resolve("5-day-old-file.xlsx");
        Files.createFile(oldFile);
        Instant oldTime = Instant.now().minus(5, ChronoUnit.DAYS);
        Files.setLastModifiedTime(oldFile, FileTime.from(oldTime));

        // Create a file 2 days old
        Path recentFile = testUploadLocation.resolve("2-day-old-file.xlsx");
        Files.createFile(recentFile);
        Instant recentTime = Instant.now().minus(2, ChronoUnit.DAYS);
        Files.setLastModifiedTime(recentFile, FileTime.from(recentTime));

        // Run cleanup
        cleanupService.cleanupOldFiles();

        // Verify cleanup based on 3-day retention
        assertFalse(Files.exists(oldFile), "5-day-old file should be deleted with 3-day retention");
        assertTrue(Files.exists(recentFile), "2-day-old file should be retained with 3-day retention");
    }

    @Test
    void testCleanupWithVeryNewFile() throws IOException {
        // Create a file just created (current time)
        Path newFile = testUploadLocation.resolve("brand-new-file.xlsx");
        Files.createFile(newFile);

        // Run cleanup
        cleanupService.cleanupOldFiles();

        // Verify the new file is retained
        assertTrue(Files.exists(newFile), "Brand new file should be retained");
    }

    @Test
    void testCleanupAllFilesDeletesAllFiles() throws IOException {
        // Create multiple files with different ages
        Path oldFile = testUploadLocation.resolve("old-file.xlsx");
        Path newFile = testUploadLocation.resolve("new-file.xlsx");

        Files.createFile(oldFile);
        Files.createFile(newFile);

        // Set different timestamps
        Instant oldTime = Instant.now().minus(10, ChronoUnit.DAYS);
        Files.setLastModifiedTime(oldFile, FileTime.from(oldTime));

        // Verify files exist before cleanup
        assertTrue(Files.exists(oldFile), "Old file should exist before cleanup");
        assertTrue(Files.exists(newFile), "New file should exist before cleanup");

        // Run cleanupAllFiles - should delete ALL files regardless of age
        cleanupService.cleanupAllFiles();

        // Verify all files were deleted
        assertFalse(Files.exists(oldFile), "Old file should be deleted");
        assertFalse(Files.exists(newFile), "New file should be deleted (regardless of age)");
    }

    @Test
    void testCleanupAllFilesWithEmptyDirectory() throws IOException {
        // Ensure directory is empty
        try (Stream<Path> files = Files.list(testUploadLocation)) {
            assertEquals(0, files.count(), "Directory should be empty");
        }

        // Run cleanupAllFiles - should complete without errors
        assertDoesNotThrow(() -> cleanupService.cleanupAllFiles());
    }

    @Test
    void testCleanupAllFilesIgnoresSubdirectories() throws IOException {
        // Create a file and a subdirectory
        Path file = testUploadLocation.resolve("test-file.xlsx");
        Path subdir = testUploadLocation.resolve("subdir");

        Files.createFile(file);
        Files.createDirectory(subdir);

        // Run cleanupAllFiles
        cleanupService.cleanupAllFiles();

        // File should be deleted, subdirectory should remain
        assertFalse(Files.exists(file), "File should be deleted");
        assertTrue(Files.exists(subdir), "Subdirectory should be ignored");

        // Clean up subdirectory for tearDown
        Files.deleteIfExists(subdir);
    }

    @Test
    void testCleanupAllFilesWithNonExistentDirectory() {
        // Create a separate service instance with a non-existent directory
        FileCleanupService testService = new FileCleanupService(xlsService);
        ReflectionTestUtils.setField(testService, "retentionDays", 7);

        // Set a non-existent upload location
        Path nonExistentPath = Paths.get(System.getProperty("java.io.tmpdir"), "uploads-cleanupall-test-" + System.currentTimeMillis());
        ReflectionTestUtils.setField(testService, "uploadLocation", nonExistentPath);

        // Run cleanupAllFiles - should handle gracefully without throwing exception
        assertDoesNotThrow(() -> testService.cleanupAllFiles());

        // Verify the directory was not created
        assertFalse(Files.exists(nonExistentPath), "Non-existent directory should remain non-existent");
    }

    @Test
    void testCleanupSkipsGeneratedTemplates() throws IOException {
        // Create generated template files (pattern: timesheet-YYYY-MM.xlsx)
        Path template1 = testUploadLocation.resolve("timesheet-2025-11.xlsx");
        Path template2 = testUploadLocation.resolve("timesheet-2025-12.xlsx");
        Path template3 = testUploadLocation.resolve("timesheet-2024-01.xlsx");

        // Create regular files that should be deleted
        Path regularOldFile1 = testUploadLocation.resolve("user-upload-old.xlsx");
        Path regularOldFile2 = testUploadLocation.resolve("2025-11.xlsx"); // Missing prefix

        Files.createFile(template1);
        Files.createFile(template2);
        Files.createFile(template3);
        Files.createFile(regularOldFile1);
        Files.createFile(regularOldFile2);

        // Make all files old (10 days)
        Instant oldTime = Instant.now().minus(10, ChronoUnit.DAYS);
        Files.setLastModifiedTime(template1, FileTime.from(oldTime));
        Files.setLastModifiedTime(template2, FileTime.from(oldTime));
        Files.setLastModifiedTime(template3, FileTime.from(oldTime));
        Files.setLastModifiedTime(regularOldFile1, FileTime.from(oldTime));
        Files.setLastModifiedTime(regularOldFile2, FileTime.from(oldTime));

        // Run cleanup
        cleanupService.cleanupOldFiles();

        // Generated templates should be preserved
        assertTrue(Files.exists(template1), "Generated template timesheet-2025-11.xlsx should be preserved");
        assertTrue(Files.exists(template2), "Generated template timesheet-2025-12.xlsx should be preserved");
        assertTrue(Files.exists(template3), "Generated template timesheet-2024-01.xlsx should be preserved");

        // Regular old files should be deleted
        assertFalse(Files.exists(regularOldFile1), "Regular old file should be deleted");
        assertFalse(Files.exists(regularOldFile2), "File without timesheet- prefix should be deleted");
    }

    @Test
    void testCleanupAllFilesSkipsGeneratedTemplates() throws IOException {
        // Create generated template files
        Path template1 = testUploadLocation.resolve("timesheet-2025-11.xlsx");
        Path template2 = testUploadLocation.resolve("timesheet-2024-12.xlsx");

        // Create regular files
        Path regularFile1 = testUploadLocation.resolve("user-upload.xlsx");
        Path regularFile2 = testUploadLocation.resolve("report.pdf");

        Files.createFile(template1);
        Files.createFile(template2);
        Files.createFile(regularFile1);
        Files.createFile(regularFile2);

        // Run cleanupAllFiles
        cleanupService.cleanupAllFiles();

        // Generated templates should be preserved even in full cleanup
        assertTrue(Files.exists(template1), "Generated template should be preserved during full cleanup");
        assertTrue(Files.exists(template2), "Generated template should be preserved during full cleanup");

        // Regular files should be deleted
        assertFalse(Files.exists(regularFile1), "Regular file should be deleted");
        assertFalse(Files.exists(regularFile2), "Regular file should be deleted");
    }

    @Test
    void testGeneratedTemplatePatternMatching() throws IOException {
        // Create files with various patterns to test prefix matching
        Path validTemplate1 = testUploadLocation.resolve("timesheet-2025-01.xlsx");
        Path validTemplate2 = testUploadLocation.resolve("timesheet-2099-12.xlsx");
        Path validTemplate3 = testUploadLocation.resolve("timesheet-any-suffix.xlsx"); // Any suffix works
        Path invalidTemplate1 = testUploadLocation.resolve("2025-11.xlsx"); // Missing prefix
        Path invalidTemplate2 = testUploadLocation.resolve("mytimesheet-2025-11.xlsx"); // Wrong prefix
        Path invalidTemplate3 = testUploadLocation.resolve("Timesheet-2025-06.xlsx"); // Wrong case
        Path invalidTemplate4 = testUploadLocation.resolve("user-timesheet-2025-11.xlsx"); // Prefix in middle
        Path regularFile = testUploadLocation.resolve("report.pdf");

        Files.createFile(validTemplate1);
        Files.createFile(validTemplate2);
        Files.createFile(validTemplate3);
        Files.createFile(invalidTemplate1);
        Files.createFile(invalidTemplate2);
        Files.createFile(invalidTemplate3);
        Files.createFile(invalidTemplate4);
        Files.createFile(regularFile);

        // Make all files old
        Instant oldTime = Instant.now().minus(10, ChronoUnit.DAYS);
        List<Path> allFiles = List.of(validTemplate1, validTemplate2, validTemplate3,
                invalidTemplate1, invalidTemplate2, invalidTemplate3, invalidTemplate4, regularFile);
        for (Path file : allFiles) {
            Files.setLastModifiedTime(file, FileTime.from(oldTime));
        }

        // Run cleanup
        cleanupService.cleanupOldFiles();

        // Only files starting with "timesheet-" should be preserved
        assertTrue(Files.exists(validTemplate1), "timesheet-2025-01.xlsx should be preserved");
        assertTrue(Files.exists(validTemplate2), "timesheet-2099-12.xlsx should be preserved");
        assertTrue(Files.exists(validTemplate3), "timesheet-any-suffix.xlsx should be preserved");

        // Files not starting with "timesheet-" should be deleted
        assertFalse(Files.exists(invalidTemplate1), "2025-11.xlsx should be deleted (missing prefix)");
        assertFalse(Files.exists(invalidTemplate2), "mytimesheet-2025-11.xlsx should be deleted (wrong prefix)");
        assertFalse(Files.exists(invalidTemplate3), "Timesheet-2025-06.xlsx should be deleted (wrong case)");
        assertFalse(Files.exists(invalidTemplate4), "user-timesheet-2025-11.xlsx should be deleted (prefix in middle)");
        assertFalse(Files.exists(regularFile), "report.pdf should be deleted");
    }

    @Test
    void testCleanupPreGeneratesTemplates() throws IOException {
        // Create an old file to be deleted
        Path oldFile = testUploadLocation.resolve("old-file.xlsx");
        Files.createFile(oldFile);
        Instant oldTime = Instant.now().minus(10, ChronoUnit.DAYS);
        Files.setLastModifiedTime(oldFile, FileTime.from(oldTime));

        // Verify old file exists before cleanup
        assertTrue(Files.exists(oldFile), "Old file should exist before cleanup");

        // Run cleanup (which should also pre-generate templates)
        cleanupService.cleanupOldFiles();

        // Verify old file was deleted
        assertFalse(Files.exists(oldFile), "Old file should be deleted");

        // Verify templates were pre-generated for previous, current, and next month
        YearMonth now = YearMonth.now();
        YearMonth[] expectedMonths = {
                now.minusMonths(1),  // Previous month
                now,                  // Current month
                now.plusMonths(1)     // Next month
        };

        for (YearMonth month : expectedMonths) {
            String period = String.format("%d-%02d", month.getYear(), month.getMonthValue());
            String filename = "timesheet-" + period + ".xlsx";
            Path templatePath = testUploadLocation.resolve(filename);

            assertTrue(Files.exists(templatePath),
                    "Template should be pre-generated for " + month + ": " + filename);
            assertTrue(Files.size(templatePath) > 0,
                    "Pre-generated template should not be empty: " + filename);
        }
    }

    @Test
    void testPreGenerationSkipsExistingTemplates() throws IOException {
        // Pre-create a template for current month
        YearMonth now = YearMonth.now();
        String period = String.format("%d-%02d", now.getYear(), now.getMonthValue());
        String filename = "timesheet-" + period + ".xlsx";
        Path existingTemplate = testUploadLocation.resolve(filename);

        // Create a placeholder file with specific content
        String originalContent = "existing template content";
        Files.writeString(existingTemplate, originalContent);
        long originalSize = Files.size(existingTemplate);

        // Run cleanup (which triggers pre-generation)
        cleanupService.cleanupOldFiles();

        // Verify the existing template was not overwritten
        assertTrue(Files.exists(existingTemplate), "Existing template should still exist");
        assertEquals(originalSize, Files.size(existingTemplate),
                "Existing template should not be modified");
        assertEquals(originalContent, Files.readString(existingTemplate),
                "Existing template content should remain unchanged");

        // Verify templates for previous and next month were created
        YearMonth[] otherMonths = {now.minusMonths(1), now.plusMonths(1)};
        for (YearMonth month : otherMonths) {
            String otherPeriod = String.format("%d-%02d", month.getYear(), month.getMonthValue());
            String otherFilename = "timesheet-" + otherPeriod + ".xlsx";
            Path otherTemplate = testUploadLocation.resolve(otherFilename);

            assertTrue(Files.exists(otherTemplate),
                    "Template should be created for " + month + ": " + otherFilename);
        }
    }
}
