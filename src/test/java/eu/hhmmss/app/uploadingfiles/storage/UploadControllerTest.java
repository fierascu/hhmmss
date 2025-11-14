package eu.hhmmss.app.uploadingfiles.storage;

import eu.hhmmss.app.converter.PdfService;
import eu.hhmmss.app.converter.ZipProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.context.TestPropertySource;

@WebMvcTest(UploadController.class)
@TestPropertySource(properties = {
        "spring.servlet.multipart.max-file-size=10MB",
        "spring.servlet.multipart.max-request-size=10MB"
})
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UploadService uploadService;

    @MockBean
    private ZipProcessingService zipProcessingService;

    @MockBean
    private PdfService pdfService;

    @MockBean
    private ThrottlingService throttlingService;

    @Test
    void testListUploadedFiles() throws Exception {
        when(uploadService.loadAll()).thenReturn(Stream.of(
                Paths.get("file1.xlsx"),
                Paths.get("file2.xlsx")
        ));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("upload"))
                .andExpect(model().attributeExists("files"));

        verify(uploadService).loadAll();
    }

    @Test
    void testListUploadedFilesWithErrorInSession() throws Exception {
        when(uploadService.loadAll()).thenReturn(Stream.empty());

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("uploadError", "Test error message");

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("upload"))
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(model().attribute("errorMessage", "Test error message"));
    }

    @Test
    void testHandleFileUploadWithInvalidContent() throws Exception {
        // Note: The controller now attempts to parse the uploaded XLS and generate DOCX.
        // Invalid XLS content that doesn't exist as a file will cause XlsService.readTimesheet()
        // to return an empty DTO (0 tasks), and DocService will successfully generate a DOCX.
        // This test has been renamed to clarify it tests invalid content handling.
        byte[] xlsxContent = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxContent
        );

        when(uploadService.store(any())).thenReturn("uuid-12345.xlsx");
        // Return a non-existent path - XlsService will catch the error and return empty DTO
        when(uploadService.load("uuid-12345.xlsx")).thenReturn(Paths.get("/tmp/nonexistent-uuid-12345.xlsx"));
        when(uploadService.load(argThat(filename ->
                filename != null && filename.startsWith("timesheet_") && filename.endsWith(".docx"))))
                .thenAnswer(invocation -> {
                    String filename = invocation.getArgument(0);
                    return Paths.get("/tmp/uploads/" + filename);
                });

        // Mock PDF service
        doNothing().when(pdfService).convertXlsToPdf(any(Path.class), any(Path.class));
        doNothing().when(pdfService).convertDocToPdf(any(Path.class), any(Path.class));

        // XlsService returns empty DTO with 0 tasks, but processing succeeds
        mockMvc.perform(multipart("/")
                        .file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("successMessage"))
                .andExpect(flash().attribute("successMessage", "File processed successfully! Extracted 0 tasks and generated PDFs."));

        verify(uploadService).store(file);
    }

    @Test
    void testHandleFileUploadWithValidXlsFile() throws Exception {
        // Load the real test XLS file
        Path testXlsPath = Paths.get("src/test/resources/timesheet-in.xlsx");
        byte[] xlsxContent = Files.readAllBytes(testXlsPath);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "timesheet-in.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxContent
        );

        when(uploadService.store(any())).thenReturn("uuid-12345.xlsx");
        when(uploadService.load("uuid-12345.xlsx")).thenReturn(testXlsPath);
        when(uploadService.load("uuid-12345.xlsx.docx"))
                .thenReturn(Paths.get("/tmp/uploads/uuid-12345.xlsx.docx"));
        when(uploadService.load("uuid-12345.xlsx.pdf"))
                .thenReturn(Paths.get("/tmp/uploads/uuid-12345.xlsx.pdf"));
        when(uploadService.load("uuid-12345.xlsx.docx.pdf"))
                .thenReturn(Paths.get("/tmp/uploads/uuid-12345.xlsx.docx.pdf"));

        // Mock PDF service
        doNothing().when(pdfService).convertXlsToPdf(any(Path.class), any(Path.class));
        doNothing().when(pdfService).convertDocToPdf(any(Path.class), any(Path.class));

        // The controller should successfully process the file and generate DOCX and PDFs
        mockMvc.perform(multipart("/")
                        .file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("successMessage"))
                .andExpect(flash().attributeExists("originalFilename"))
                .andExpect(flash().attributeExists("uuidFilename"))
                .andExpect(flash().attributeExists("extractedFilename"))
                .andExpect(flash().attributeExists("xlsPdfFilename"))
                .andExpect(flash().attributeExists("docPdfFilename"))
                .andExpect(flash().attributeExists("isZipResult"))
                .andExpect(flash().attribute("originalFilename", "timesheet-in.xlsx"))
                .andExpect(flash().attribute("isZipResult", false));

        verify(uploadService).store(file);
        verify(pdfService).convertXlsToPdf(any(Path.class), any(Path.class));
        verify(pdfService).convertDocToPdf(any(Path.class), any(Path.class));
    }

    @Test
    void testHandleFileUploadStorageException() throws Exception {
        byte[] invalidContent = {0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invalid.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                invalidContent
        );

        when(uploadService.store(any())).thenThrow(new StorageException("Invalid file format"));

        mockMvc.perform(multipart("/")
                        .file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("errorMessage"))
                .andExpect(flash().attribute("errorMessage", "Invalid file format"));

        verify(uploadService).store(file);
    }

    @Test
    void testHandleFileUploadGenericException() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{0x50, 0x4B}
        );

        when(uploadService.store(any())).thenThrow(new RuntimeException("Unexpected error"));

        mockMvc.perform(multipart("/")
                        .file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("errorMessage"))
                .andExpect(flash().attribute("errorMessage", containsString("An unexpected error occurred")));

        verify(uploadService).store(file);
    }

    @Test
    void testServeFile() throws Exception {
        byte[] content = "test content".getBytes();
        Resource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "test.xlsx";
            }
        };

        when(uploadService.loadAsResource("test.xlsx")).thenReturn(resource);

        mockMvc.perform(get("/files/test.xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"test.xlsx\""))
                .andExpect(content().bytes(content));

        verify(uploadService).loadAsResource("test.xlsx");
    }

    @Test
    void testServeFileNotFound() throws Exception {
        when(uploadService.loadAsResource("nonexistent.xlsx"))
                .thenThrow(new StorageFileNotFoundException("File not found"));

        mockMvc.perform(get("/files/nonexistent.xlsx"))
                .andExpect(status().isNotFound());

        verify(uploadService).loadAsResource("nonexistent.xlsx");
    }

    @Test
    void testServeFileReturnsNullResource() throws Exception {
        when(uploadService.loadAsResource(anyString())).thenReturn(null);

        mockMvc.perform(get("/files/test.xlsx"))
                .andExpect(status().isNotFound());

        verify(uploadService).loadAsResource("test.xlsx");
    }

    @Test
    void testServeFileWithSpecialCharactersInFilename() throws Exception {
        String filename = "test-file_123.xlsx";
        byte[] content = "test content".getBytes();
        Resource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        when(uploadService.loadAsResource(filename)).thenReturn(resource);

        mockMvc.perform(get("/files/" + filename))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + filename + "\""));

        verify(uploadService).loadAsResource(filename);
    }

    @Test
    void testHandleFileUploadWithPdfConversionFailure() throws Exception {
        // Load the real test XLS file
        Path testXlsPath = Paths.get("src/test/resources/timesheet-in.xlsx");
        byte[] xlsxContent = Files.readAllBytes(testXlsPath);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "timesheet-in.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxContent
        );

        when(uploadService.store(any())).thenReturn("uuid-12345.xlsx");
        when(uploadService.load("uuid-12345.xlsx")).thenReturn(testXlsPath);
        when(uploadService.load("uuid-12345.xlsx.docx"))
                .thenReturn(Paths.get("/tmp/uploads/uuid-12345.xlsx.docx"));
        when(uploadService.load("uuid-12345.xlsx.pdf"))
                .thenReturn(Paths.get("/tmp/uploads/uuid-12345.xlsx.pdf"));

        // Mock PDF service to throw exception
        doThrow(new RuntimeException("PDF conversion failed")).when(pdfService).convertXlsToPdf(any(Path.class), any(Path.class));

        // The controller should handle the exception gracefully
        mockMvc.perform(multipart("/")
                        .file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("errorMessage"))
                .andExpect(flash().attribute("errorMessage", containsString("An unexpected error occurred")));

        verify(uploadService).store(file);
        verify(pdfService).convertXlsToPdf(any(Path.class), any(Path.class));
    }

    @Test
    void testHandleFileUploadWithDocPdfConversionFailure() throws Exception {
        // Load the real test XLS file
        Path testXlsPath = Paths.get("src/test/resources/timesheet-in.xlsx");
        byte[] xlsxContent = Files.readAllBytes(testXlsPath);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "timesheet-in.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxContent
        );

        when(uploadService.store(any())).thenReturn("uuid-12345.xlsx");
        when(uploadService.load("uuid-12345.xlsx")).thenReturn(testXlsPath);
        when(uploadService.load("uuid-12345.xlsx.docx"))
                .thenReturn(Paths.get("/tmp/uploads/uuid-12345.xlsx.docx"));
        when(uploadService.load("uuid-12345.xlsx.pdf"))
                .thenReturn(Paths.get("/tmp/uploads/uuid-12345.xlsx.pdf"));
        when(uploadService.load("uuid-12345.xlsx.docx.pdf"))
                .thenReturn(Paths.get("/tmp/uploads/uuid-12345.xlsx.docx.pdf"));

        // Mock PDF service - XLS to PDF succeeds, DOC to PDF fails
        doNothing().when(pdfService).convertXlsToPdf(any(Path.class), any(Path.class));
        doThrow(new RuntimeException("DOC to PDF conversion failed")).when(pdfService).convertDocToPdf(any(Path.class), any(Path.class));

        // The controller should handle the exception gracefully
        mockMvc.perform(multipart("/")
                        .file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("errorMessage"))
                .andExpect(flash().attribute("errorMessage", containsString("An unexpected error occurred")));

        verify(uploadService).store(file);
        verify(pdfService).convertXlsToPdf(any(Path.class), any(Path.class));
        verify(pdfService).convertDocToPdf(any(Path.class), any(Path.class));
    }

    @Test
    void testHandleFileUploadWithOversizedXlsxFile() throws Exception {
        // Create a file that exceeds 128KB limit
        byte[] largeContent = new byte[132 * 1024]; // 132KB
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                largeContent
        );

        // File size validation happens before store is called
        mockMvc.perform(multipart("/")
                        .file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("errorMessage"))
                .andExpect(flash().attribute("errorMessage", containsString("Excel file size")))
                .andExpect(flash().attribute("errorMessage", containsString("exceeds the maximum limit of 128 KB")));

        // Store should never be called because validation fails first
        verify(uploadService, never()).store(any());
    }

    @Test
    void testHandleFileUploadWithOversizedZipFile() throws Exception {
        // Create a file that exceeds 2MB limit
        byte[] largeContent = new byte[3 * 1024 * 1024]; // 3MB
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.zip",
                "application/zip",
                largeContent
        );

        // File size validation happens before store is called
        mockMvc.perform(multipart("/")
                        .file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("errorMessage"))
                .andExpect(flash().attribute("errorMessage", containsString("ZIP file size")))
                .andExpect(flash().attribute("errorMessage", containsString("exceeds the maximum limit of 2.00 MB")));

        // Store should never be called because validation fails first
        verify(uploadService, never()).store(any());
    }

    @Test
    void testHandleFileUploadWithValidSizedXlsxFile() throws Exception {
        // Create a file within 128KB limit
        byte[] validContent = new byte[100 * 1024]; // 100KB
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "valid.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                validContent
        );

        when(uploadService.store(any())).thenReturn("uuid-12345.xlsx");
        when(uploadService.load("uuid-12345.xlsx")).thenReturn(Paths.get("/tmp/test.xlsx"));

        // File should pass size validation
        mockMvc.perform(multipart("/")
                        .file(file))
                .andExpect(status().is3xxRedirection());

        verify(uploadService).store(file);
    }

    @Test
    void testHandleFileUploadWithValidSizedZipFile() throws Exception {
        // Create a file within 2MB limit
        byte[] validContent = new byte[1024 * 1024]; // 1MB
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "valid.zip",
                "application/zip",
                validContent
        );

        when(uploadService.store(any())).thenReturn("uuid-12345.zip");
        when(uploadService.load("uuid-12345.zip")).thenReturn(Paths.get("/tmp/test.zip"));

        // File should pass size validation
        mockMvc.perform(multipart("/")
                        .file(file))
                .andExpect(status().is3xxRedirection());

        verify(uploadService).store(file);
    }

    @Test
    void testThrottlingServiceAcquireAndReleasePermit() throws Exception {
        // Test that throttling service is called
        byte[] content = new byte[1024];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );

        when(uploadService.store(any())).thenReturn("uuid-12345.xlsx");
        when(uploadService.load("uuid-12345.xlsx")).thenReturn(Paths.get("/tmp/test.xlsx"));

        mockMvc.perform(multipart("/")
                        .file(file))
                .andExpect(status().is3xxRedirection());

        // Verify acquirePermit and releasePermit were called
        verify(throttlingService, times(1)).acquirePermit();
        verify(throttlingService, times(1)).releasePermit();
    }

    @Test
    void testThrottlingServiceReleasesPermitOnException() throws Exception {
        byte[] content = new byte[1024];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );

        // Make store throw an exception
        when(uploadService.store(any())).thenThrow(new StorageException("Storage error"));

        mockMvc.perform(multipart("/")
                        .file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));

        // Verify permit is released even when exception occurs
        verify(throttlingService, times(1)).acquirePermit();
        verify(throttlingService, times(1)).releasePermit();
    }

    @Test
    void testThrottlingServiceTooManyRequests() throws Exception {
        byte[] content = new byte[1024];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );

        // Make acquirePermit throw TooManyRequestsException
        doThrow(new TooManyRequestsException("Server is busy"))
                .when(throttlingService).acquirePermit();

        mockMvc.perform(multipart("/")
                        .file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        // Verify acquirePermit was called but store was never called
        verify(throttlingService, times(1)).acquirePermit();
        verify(uploadService, never()).store(any());
        // releasePermit should not be called when acquirePermit throws
        verify(throttlingService, never()).releasePermit();
    }

    @Test
    void testHandleGenerateWithValidPeriod() throws Exception {
        String period = "2024-11";
        Path testTemplatePath = Paths.get("src/main/resources/timesheet-template.xlsx");

        when(uploadService.load("2024-11.xlsx")).thenReturn(Paths.get("/tmp/uploads/2024-11.xlsx"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/generate")
                        .param("period", period))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("successMessage"))
                .andExpect(flash().attributeExists("generatedFiles"))
                .andExpect(flash().attributeExists("generatedFileUrls"))
                .andExpect(flash().attribute("isZipResult", false));

        verify(throttlingService, times(1)).acquirePermit();
        verify(throttlingService, times(1)).releasePermit();
    }

    @Test
    void testHandleGenerateWithoutPeriod() throws Exception {
        // Should default to current month and year
        java.time.LocalDate now = java.time.LocalDate.now();
        String expectedFilename = String.format("%d-%02d.xlsx", now.getYear(), now.getMonthValue());

        when(uploadService.load(expectedFilename)).thenReturn(Paths.get("/tmp/uploads/" + expectedFilename));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/generate"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("successMessage"))
                .andExpect(flash().attributeExists("generatedFiles"));

        verify(throttlingService, times(1)).acquirePermit();
        verify(throttlingService, times(1)).releasePermit();
    }

    @Test
    void testHandleGenerateWithEmptyPeriod() throws Exception {
        // Empty period should default to current month and year
        java.time.LocalDate now = java.time.LocalDate.now();
        String expectedFilename = String.format("%d-%02d.xlsx", now.getYear(), now.getMonthValue());

        when(uploadService.load(expectedFilename)).thenReturn(Paths.get("/tmp/uploads/" + expectedFilename));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/generate")
                        .param("period", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(throttlingService, times(1)).acquirePermit();
        verify(throttlingService, times(1)).releasePermit();
    }

    @Test
    void testHandleGenerateWithTheme() throws Exception {
        String period = "2024-11";
        when(uploadService.load("2024-11.xlsx")).thenReturn(Paths.get("/tmp/uploads/2024-11.xlsx"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/generate")
                        .param("period", period)
                        .param("theme", "classic"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?theme=classic"));

        verify(throttlingService, times(1)).acquirePermit();
        verify(throttlingService, times(1)).releasePermit();
    }

    @Test
    void testHandleGenerateUsesCache() throws Exception {
        String period = "2024-11";
        Path testExistingPath = Paths.get("src/test/resources/timesheet-in.xlsx");

        // Return a path that actually exists to simulate cache hit
        when(uploadService.load("2024-11.xlsx")).thenReturn(testExistingPath);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/generate")
                        .param("period", period))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("successMessage"))
                .andExpect(flash().attribute("successMessage", containsString("retrieved from cache")));

        verify(throttlingService, times(1)).acquirePermit();
        verify(throttlingService, times(1)).releasePermit();
    }
}
