package eu.hhmmss.app.uploadingfiles.storage;

import eu.hhmmss.app.util.FileHasher;
import eu.hhmmss.app.util.TimeBasedUuidGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadServiceTest {

    private UploadService uploadService;
    private Path testRootLocation;
    private FileCleanupService mockFileCleanupService;
    private FileOwnershipService mockFileOwnershipService;
    private static final String TEST_SESSION_ID = "test-session-id";

    @BeforeEach
    void setUp() throws IOException {
        // Create mocks
        mockFileCleanupService = mock(FileCleanupService.class);
        mockFileOwnershipService = mock(FileOwnershipService.class);

        uploadService = new UploadService(mockFileCleanupService, mockFileOwnershipService);
        uploadService.initialize();

        // Get the actual root location used by the service
        // This will be in the system temp directory
        String tempDir = System.getProperty("java.io.tmpdir");
        testRootLocation = Paths.get(tempDir, "uploads");
    }

    @AfterEach
    void tearDown() {
        // Clean up test files
        try {
            if (Files.exists(testRootLocation)) {
                uploadService.deleteAll();
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    void testInitialize() {
        assertTrue(Files.exists(testRootLocation), "Upload directory should be created");
        assertTrue(Files.isDirectory(testRootLocation), "Upload location should be a directory");

        // Verify that cleanupAllFiles was called during initialization
        verify(mockFileCleanupService, times(1)).cleanupAllFiles();
    }

    @Test
    void testStoreValidExcelFile() throws IOException {
        // Create a valid XLSX file (PK magic bytes for ZIP)
        byte[] xlsxContent = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxContent
        );

        String storedFilename = uploadService.store(file, TEST_SESSION_ID);

        assertNotNull(storedFilename);
        assertTrue(storedFilename.endsWith(".xlsx"));
        assertTrue(Files.exists(testRootLocation.resolve(storedFilename)));
    }

    @Test
    void testStoreValidXlsFile() throws IOException {
        // Create a valid XLS file (OLE2 magic bytes)
        byte[] xlsContent = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xls",
                "application/vnd.ms-excel",
                xlsContent
        );

        String storedFilename = uploadService.store(file, TEST_SESSION_ID);

        assertNotNull(storedFilename);
        assertTrue(storedFilename.endsWith(".xls"));
        assertTrue(Files.exists(testRootLocation.resolve(storedFilename)));
    }

    @Test
    void testStoreEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[0]
        );

        StorageException exception = assertThrows(StorageException.class,
                () -> uploadService.store(file, TEST_SESSION_ID));

        assertEquals("Failed to store empty file.", exception.getMessage());
    }

    @Test
    void testStoreInvalidFileType() {
        // Create a file with wrong magic bytes
        byte[] invalidContent = {0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invalid.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                invalidContent
        );

        StorageException exception = assertThrows(StorageException.class,
                () -> uploadService.store(file, TEST_SESSION_ID));

        assertTrue(exception.getMessage().contains("not a valid ZIP file"));
    }

    @Test
    void testStoreExecutableFile() {
        // Create a file with executable magic bytes
        byte[] exeContent = {0x4D, 0x5A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malicious.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                exeContent
        );

        StorageException exception = assertThrows(StorageException.class,
                () -> uploadService.store(file, TEST_SESSION_ID));

        assertTrue(exception.getMessage().contains("Windows executable"));
    }

    @Test
    void testStoreFileWithPathTraversal() {
        byte[] xlsxContent = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};

        // Note: The actual path traversal is prevented by UUID filename generation
        // This test verifies the file is stored with a safe UUID name
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../../etc/passwd.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxContent
        );

        String storedFilename = uploadService.store(file, TEST_SESSION_ID);

        // The stored filename should be a UUID, not the malicious path
        assertNotNull(storedFilename);
        assertFalse(storedFilename.contains(".."));
        assertFalse(storedFilename.contains("/"));
        assertTrue(storedFilename.endsWith(".xlsx"));
    }

    @Test
    void testLoad() throws IOException {
        // Store a file first
        byte[] xlsxContent = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxContent
        );
        String storedFilename = uploadService.store(file, TEST_SESSION_ID);

        Path loadedPath = uploadService.load(storedFilename);

        assertNotNull(loadedPath);
        assertEquals(testRootLocation.resolve(storedFilename), loadedPath);
    }

    @Test
    void testLoadAsResource() throws IOException {
        // Store a file first
        byte[] xlsxContent = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxContent
        );
        String storedFilename = uploadService.store(file, TEST_SESSION_ID);

        Resource resource = uploadService.loadAsResource(storedFilename);

        assertNotNull(resource);
        assertTrue(resource.exists());
        assertTrue(resource.isReadable());
    }

    @Test
    void testLoadAsResourceNotFound() {
        StorageFileNotFoundException exception = assertThrows(StorageFileNotFoundException.class,
                () -> uploadService.loadAsResource("nonexistent.xlsx"));

        assertTrue(exception.getMessage().contains("Could not read file: nonexistent.xlsx"));
    }

    @Test
    void testLoadAll() throws IOException {
        // Store multiple files
        byte[] xlsxContent = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};

        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "test1.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxContent
        );
        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "test2.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxContent
        );

        uploadService.store(file1, TEST_SESSION_ID);
        uploadService.store(file2, TEST_SESSION_ID);

        Stream<Path> allFiles = uploadService.loadAll();
        List<Path> fileList = allFiles.toList();

        assertEquals(2, fileList.size());
    }

    @Test
    void testStoreValidZipFile() throws IOException {
        // Create a valid ZIP file (PK magic bytes)
        byte[] zipContent = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "timesheets.zip",
                "application/zip",
                zipContent
        );

        String storedFilename = uploadService.store(file, TEST_SESSION_ID);

        assertNotNull(storedFilename);
        assertTrue(storedFilename.endsWith(".zip"));
        assertTrue(Files.exists(testRootLocation.resolve(storedFilename)));
    }

    @Test
    void testStoreFilePreservesExtension() throws IOException {
        byte[] xlsmContent = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "workbook.xlsm",
                "application/vnd.ms-excel.sheet.macroEnabled.12",
                xlsmContent
        );

        String storedFilename = uploadService.store(file, TEST_SESSION_ID);

        assertTrue(storedFilename.endsWith(".xlsm"), "File extension should be preserved");
    }

    @Test
    void testStoreFileWithoutExtension() {
        byte[] xlsxContent = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "noextension",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxContent
        );

        // This should throw an exception because the validator checks for Excel or ZIP extensions
        StorageException exception = assertThrows(StorageException.class,
                () -> uploadService.store(file, TEST_SESSION_ID));

        assertTrue(exception.getMessage().contains("Excel or ZIP extension"));
    }

    @Test
    void testInit() throws IOException {
        // Test the init method (requires initialize to be called first to set rootLocation)
        FileCleanupService mockCleanupService = mock(FileCleanupService.class);
        FileOwnershipService mockOwnershipService = mock(FileOwnershipService.class);
        UploadService service = new UploadService(mockCleanupService, mockOwnershipService);
        service.initialize(); // Must call initialize first to set rootLocation

        assertDoesNotThrow(() -> service.init());

        // Verify the directory was created
        String tempDir = System.getProperty("java.io.tmpdir");
        Path expectedPath = Paths.get(tempDir, "uploads");
        assertTrue(Files.exists(expectedPath));
    }

    // ===== New tests for UUID+hash naming scheme =====

    @Test
    void testStoreUsesSecureRandomUuid() throws IOException {
        byte[] xlsxContent = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxContent
        );

        String storedFilename = uploadService.store(file, TEST_SESSION_ID);

        // Extract UUID from filename (format: sessionID_UUID-hash.extension)
        String uuidPart = extractUuidFromFilename(storedFilename);
        UUID uuid = UUID.fromString(uuidPart);

        // Verify it's a version 4 (random) UUID for security
        assertEquals(4, uuid.version(),
                "Stored filename should use random UUID v4 for security");
    }

    @Test
    void testStoreIncludesFileHash() throws IOException {
        byte[] xlsxContent = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxContent
        );

        String storedFilename = uploadService.store(file, TEST_SESSION_ID);

        // Compute expected hash
        String expectedHash = FileHasher.computeShortHash(new ByteArrayInputStream(xlsxContent));

        // Verify filename contains the hash
        assertTrue(storedFilename.contains(expectedHash),
                "Stored filename should contain the file hash");
    }

    @Test
    void testStoreFilenameFormat() throws IOException {
        byte[] xlsxContent = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxContent
        );

        String storedFilename = uploadService.store(file, TEST_SESSION_ID);

        // Verify format: sessionID_UUID-hash.extension
        // Pattern: sessionID_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx-hash.xlsx
        assertTrue(storedFilename.matches(
                "[a-zA-Z0-9]{1,12}_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-[0-9a-f]{16}\\.xlsx"),
                "Filename should match pattern: sessionID_UUID-hash.extension"
        );
    }

    @Test
    void testStoreSameContentProducesSameHash() throws IOException {
        byte[] xlsxContent = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};

        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "test1.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxContent
        );
        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "test2.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxContent
        );

        String storedFilename1 = uploadService.store(file1, TEST_SESSION_ID);
        String storedFilename2 = uploadService.store(file2, TEST_SESSION_ID);

        // Extract hashes from filenames
        String hash1 = extractHashFromFilename(storedFilename1);
        String hash2 = extractHashFromFilename(storedFilename2);

        // Same content should produce same hash
        assertEquals(hash1, hash2,
                "Files with identical content should have the same hash");

        // But UUIDs should be different (time-based)
        String uuid1 = extractUuidFromFilename(storedFilename1);
        String uuid2 = extractUuidFromFilename(storedFilename2);
        assertNotEquals(uuid1, uuid2,
                "Files should have different time-based UUIDs");
    }

    @Test
    void testStoreDifferentContentProducesDifferentHash() throws IOException {
        byte[] xlsxContent1 = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        byte[] xlsxContent2 = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x01}; // Different

        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "test1.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxContent1
        );
        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "test2.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxContent2
        );

        String storedFilename1 = uploadService.store(file1, TEST_SESSION_ID);
        String storedFilename2 = uploadService.store(file2, TEST_SESSION_ID);

        // Extract hashes from filenames
        String hash1 = extractHashFromFilename(storedFilename1);
        String hash2 = extractHashFromFilename(storedFilename2);

        // Different content should produce different hashes
        assertNotEquals(hash1, hash2,
                "Files with different content should have different hashes");
    }

    @Test
    void testStorePreservesExtensionWithNewNaming() throws IOException {
        // Test with different extensions
        byte[] content = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};

        MockMultipartFile xlsxFile = new MockMultipartFile(
                "file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );
        MockMultipartFile zipFile = new MockMultipartFile(
                "file", "test.zip",
                "application/zip",
                content
        );

        String xlsxFilename = uploadService.store(xlsxFile, TEST_SESSION_ID);
        String zipFilename = uploadService.store(zipFile, TEST_SESSION_ID);

        assertTrue(xlsxFilename.endsWith(".xlsx"),
                "XLSX extension should be preserved");
        assertTrue(zipFilename.endsWith(".zip"),
                "ZIP extension should be preserved");
    }

    @Test
    void testUuidIsUnpredictableAndSecure() throws IOException {
        byte[] xlsxContent = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxContent
        );

        String storedFilename = uploadService.store(file, TEST_SESSION_ID);

        // Extract UUID from filename
        String uuidString = extractUuidFromFilename(storedFilename);
        UUID uuid = UUID.fromString(uuidString);

        // Verify it's a version 4 UUID (random, unpredictable)
        assertEquals(4, uuid.version(),
                "UUID should be version 4 (random) to prevent enumeration attacks");
        assertEquals(2, uuid.variant(),
                "UUID should have RFC 4122 variant");
    }

    // Helper methods for extracting UUID and hash from filename

    private String extractUuidFromFilename(String filename) {
        // Format: sessionID_UUID-hash.extension
        // First remove the session ID prefix (everything before the underscore)
        int underscoreIndex = filename.indexOf('_');
        if (underscoreIndex == -1) {
            fail("Invalid filename format (no underscore): " + filename);
            return null;
        }

        String withoutSessionId = filename.substring(underscoreIndex + 1);

        // UUID is the first 5 parts separated by hyphens
        String[] parts = withoutSessionId.split("-");
        if (parts.length >= 5) {
            return parts[0] + "-" + parts[1] + "-" + parts[2] + "-" + parts[3] + "-" + parts[4];
        }
        fail("Invalid filename format: " + filename);
        return null;
    }

    private String extractHashFromFilename(String filename) {
        // Format: sessionID_UUID-hash.extension
        // First remove the session ID prefix (everything before the underscore)
        int underscoreIndex = filename.indexOf('_');
        if (underscoreIndex == -1) {
            fail("Invalid filename format (no underscore): " + filename);
            return null;
        }

        String withoutSessionId = filename.substring(underscoreIndex + 1);

        // Hash is after the 5th hyphen and before the dot
        String[] parts = withoutSessionId.split("-");
        if (parts.length >= 6) {
            String lastPart = parts[5];
            // Remove extension
            return lastPart.split("\\.")[0];
        }
        fail("Invalid filename format: " + filename);
        return null;
    }
}
