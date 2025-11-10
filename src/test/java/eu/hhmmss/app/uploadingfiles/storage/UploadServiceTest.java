package eu.hhmmss.app.uploadingfiles.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class UploadServiceTest {

    private UploadService uploadService;
    private Path testRootLocation;

    @BeforeEach
    void setUp() throws IOException {
        uploadService = new UploadService();
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

        String storedFilename = uploadService.store(file);

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

        String storedFilename = uploadService.store(file);

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
                () -> uploadService.store(file));

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
                () -> uploadService.store(file));

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
                () -> uploadService.store(file));

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

        String storedFilename = uploadService.store(file);

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
        String storedFilename = uploadService.store(file);

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
        String storedFilename = uploadService.store(file);

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

        uploadService.store(file1);
        uploadService.store(file2);

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

        String storedFilename = uploadService.store(file);

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

        String storedFilename = uploadService.store(file);

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
                () -> uploadService.store(file));

        assertTrue(exception.getMessage().contains("Excel or ZIP extension"));
    }

    @Test
    void testInit() throws IOException {
        // Test the init method (requires initialize to be called first to set rootLocation)
        UploadService service = new UploadService();
        service.initialize(); // Must call initialize first to set rootLocation

        assertDoesNotThrow(() -> service.init());

        // Verify the directory was created
        String tempDir = System.getProperty("java.io.tmpdir");
        Path expectedPath = Paths.get(tempDir, "uploads");
        assertTrue(Files.exists(expectedPath));
    }
}
