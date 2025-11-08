package eu.hhmmss.app.uploadingfiles.storage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UploadController.class)
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UploadService uploadService;

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
    void testHandleFileUploadSuccess() throws Exception {
        byte[] xlsxContent = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxContent
        );

        when(uploadService.store(any())).thenReturn("uuid-12345.xlsx");

        mockMvc.perform(multipart("/")
                        .file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("originalFilename"))
                .andExpect(flash().attributeExists("uuidFilename"))
                .andExpect(flash().attribute("originalFilename", "test.xlsx"))
                .andExpect(flash().attribute("uuidFilename", "uuid-12345.xlsx"));

        verify(uploadService).store(file);
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
}
