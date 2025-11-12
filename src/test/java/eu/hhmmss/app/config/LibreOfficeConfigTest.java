package eu.hhmmss.app.config;

import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.office.OfficeManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for LibreOfficeConfig validation logic.
 * Tests the graceful error handling when LibreOffice is not properly configured.
 */
class LibreOfficeConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void testLibreOfficeStatus_WithNullPath() {
        LibreOfficeConfig config = new LibreOfficeConfig();
        ReflectionTestUtils.setField(config, "officeHome", null);

        LibreOfficeConfig.LibreOfficeStatus status = config.libreOfficeStatus();

        assertFalse(status.isAvailable(), "LibreOffice should not be available with null path");
        assertNull(status.getConfiguredPath(), "Configured path should be null");
    }

    @Test
    void testLibreOfficeStatus_WithEmptyPath() {
        LibreOfficeConfig config = new LibreOfficeConfig();
        ReflectionTestUtils.setField(config, "officeHome", "");

        LibreOfficeConfig.LibreOfficeStatus status = config.libreOfficeStatus();

        assertFalse(status.isAvailable(), "LibreOffice should not be available with empty path");
        assertEquals("", status.getConfiguredPath(), "Configured path should be empty string");
    }

    @Test
    void testLibreOfficeStatus_WithNonExistentPath() {
        LibreOfficeConfig config = new LibreOfficeConfig();
        ReflectionTestUtils.setField(config, "officeHome", "/non/existent/path");

        LibreOfficeConfig.LibreOfficeStatus status = config.libreOfficeStatus();

        assertFalse(status.isAvailable(), "LibreOffice should not be available with non-existent path");
        assertEquals("/non/existent/path", status.getConfiguredPath());
    }

    @Test
    void testLibreOfficeStatus_WithExistingPathButNoExecutable() throws IOException {
        // Create a directory without soffice executable
        Path libreOfficeDir = tempDir.resolve("libreoffice");
        Files.createDirectories(libreOfficeDir);

        LibreOfficeConfig config = new LibreOfficeConfig();
        ReflectionTestUtils.setField(config, "officeHome", libreOfficeDir.toString());

        LibreOfficeConfig.LibreOfficeStatus status = config.libreOfficeStatus();

        assertFalse(status.isAvailable(),
                "LibreOffice should not be available when directory exists but soffice executable is missing");
        assertEquals(libreOfficeDir.toString(), status.getConfiguredPath());
    }

    @Test
    void testLibreOfficeStatus_WithValidPath() throws IOException {
        // Create a directory structure with a mock soffice executable
        Path libreOfficeDir = tempDir.resolve("libreoffice");
        Path programDir = libreOfficeDir.resolve("program");
        Files.createDirectories(programDir);

        // Create a mock soffice file (on Unix-like systems)
        Path sofficeExecutable = programDir.resolve("soffice");
        Files.createFile(sofficeExecutable);

        LibreOfficeConfig config = new LibreOfficeConfig();
        ReflectionTestUtils.setField(config, "officeHome", libreOfficeDir.toString());

        LibreOfficeConfig.LibreOfficeStatus status = config.libreOfficeStatus();

        assertTrue(status.isAvailable(),
                "LibreOffice should be available when directory and soffice executable exist");
        assertEquals(libreOfficeDir.toString(), status.getConfiguredPath());
    }

    @Test
    void testLibreOfficeStatus_WithValidPath_Windows() throws IOException {
        // Create a directory structure with a mock soffice.exe executable (Windows)
        Path libreOfficeDir = tempDir.resolve("libreoffice");
        Path programDir = libreOfficeDir.resolve("program");
        Files.createDirectories(programDir);

        // Create a mock soffice.exe file
        Path sofficeExecutable = programDir.resolve("soffice.exe");
        Files.createFile(sofficeExecutable);

        LibreOfficeConfig config = new LibreOfficeConfig();
        ReflectionTestUtils.setField(config, "officeHome", libreOfficeDir.toString());

        LibreOfficeConfig.LibreOfficeStatus status = config.libreOfficeStatus();

        assertTrue(status.isAvailable(),
                "LibreOffice should be available when directory and soffice.exe executable exist");
        assertEquals(libreOfficeDir.toString(), status.getConfiguredPath());
    }

    @Test
    void testOfficeManager_WithInvalidPath_ReturnsNull() {
        LibreOfficeConfig config = new LibreOfficeConfig();
        ReflectionTestUtils.setField(config, "officeHome", "/invalid/path");
        ReflectionTestUtils.setField(config, "portNumbers", "2002");
        ReflectionTestUtils.setField(config, "maxTasksPerProcess", 10);

        OfficeManager officeManager = config.officeManager();

        assertNull(officeManager,
                "OfficeManager should be null when LibreOffice path is invalid");
    }

    @Test
    void testDocumentConverter_WithNullOfficeManager_ReturnsNull() {
        LibreOfficeConfig config = new LibreOfficeConfig();

        DocumentConverter documentConverter = config.documentConverter(null);

        assertNull(documentConverter,
                "DocumentConverter should be null when OfficeManager is null");
    }

    @Test
    void testLibreOfficeStatus_Constructor() {
        LibreOfficeConfig.LibreOfficeStatus status =
                new LibreOfficeConfig.LibreOfficeStatus(true, "/usr/lib/libreoffice");

        assertTrue(status.isAvailable(), "Status should reflect availability");
        assertEquals("/usr/lib/libreoffice", status.getConfiguredPath(),
                "Configured path should be set correctly");
    }

    @Test
    void testCheckForSofficeExecutable_MultiplePaths() throws IOException {
        // Test that the config checks for soffice in multiple common locations
        Path libreOfficeDir = tempDir.resolve("libreoffice");
        Path programDir = libreOfficeDir.resolve("program");
        Files.createDirectories(programDir);

        // Create soffice.bin instead of soffice
        Path sofficeExecutable = programDir.resolve("soffice.bin");
        Files.createFile(sofficeExecutable);

        LibreOfficeConfig config = new LibreOfficeConfig();
        ReflectionTestUtils.setField(config, "officeHome", libreOfficeDir.toString());

        LibreOfficeConfig.LibreOfficeStatus status = config.libreOfficeStatus();

        assertTrue(status.isAvailable(),
                "LibreOffice should be available when soffice.bin exists");
    }

    @Test
    void testValidateLibreOfficePath_EmptyPath() {
        // This test verifies that validateLibreOfficePath doesn't throw exceptions
        LibreOfficeConfig config = new LibreOfficeConfig();
        ReflectionTestUtils.setField(config, "officeHome", "");

        // Should not throw any exception
        assertDoesNotThrow(() -> config.validateLibreOfficePath(),
                "validateLibreOfficePath should not throw exception with empty path");
    }

    @Test
    void testValidateLibreOfficePath_NullPath() {
        LibreOfficeConfig config = new LibreOfficeConfig();
        ReflectionTestUtils.setField(config, "officeHome", null);

        // Should not throw any exception
        assertDoesNotThrow(() -> config.validateLibreOfficePath(),
                "validateLibreOfficePath should not throw exception with null path");
    }

    @Test
    void testValidateLibreOfficePath_InvalidPath() {
        LibreOfficeConfig config = new LibreOfficeConfig();
        ReflectionTestUtils.setField(config, "officeHome", "/non/existent/path");

        // Should not throw any exception, just log warnings
        assertDoesNotThrow(() -> config.validateLibreOfficePath(),
                "validateLibreOfficePath should not throw exception with invalid path");
    }

    @Test
    void testValidateLibreOfficePath_ValidPath() throws IOException {
        Path libreOfficeDir = tempDir.resolve("libreoffice");
        Path programDir = libreOfficeDir.resolve("program");
        Files.createDirectories(programDir);
        Path sofficeExecutable = programDir.resolve("soffice");
        Files.createFile(sofficeExecutable);

        LibreOfficeConfig config = new LibreOfficeConfig();
        ReflectionTestUtils.setField(config, "officeHome", libreOfficeDir.toString());

        // Should not throw any exception
        assertDoesNotThrow(() -> config.validateLibreOfficePath(),
                "validateLibreOfficePath should not throw exception with valid path");
    }
}
