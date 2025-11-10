package eu.hhmmss.app.converter;

import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.job.ConversionJobWithOptionalSourceFormatUnspecified;
import org.jodconverter.core.job.ConversionJobWithOptionalTargetFormatUnspecified;
import org.jodconverter.core.office.OfficeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdfServiceTest {

    @Mock
    private DocumentConverter documentConverter;

    @Mock
    private ConversionJobWithOptionalSourceFormatUnspecified sourceJob;

    @Mock
    private ConversionJobWithOptionalTargetFormatUnspecified targetJob;

    private PdfService pdfService;

    @BeforeEach
    void setUp() {
        pdfService = new PdfService(documentConverter);
    }

    @Test
    void testConvertXlsToPdf_Success() throws IOException, OfficeException {
        // Create temporary test files
        Path xlsPath = Files.createTempFile("test", ".xlsx");
        Path pdfPath = Files.createTempFile("output", ".pdf");

        try {
            // Mock the document converter chain
            when(documentConverter.convert(any(File.class))).thenReturn(sourceJob);
            when(sourceJob.to(any(File.class))).thenReturn(targetJob);
            doNothing().when(targetJob).execute();

            // Execute conversion
            pdfService.convertXlsToPdf(xlsPath, pdfPath);

            // Verify the conversion chain was called
            verify(documentConverter).convert(xlsPath.toFile());
            verify(sourceJob).to(pdfPath.toFile());
            verify(targetJob).execute();
        } finally {
            // Clean up
            Files.deleteIfExists(xlsPath);
            Files.deleteIfExists(pdfPath);
        }
    }

    @Test
    void testConvertXlsToPdf_OfficeException() throws OfficeException {
        // Create temporary test files
        Path xlsPath = null;
        Path pdfPath = null;

        try {
            xlsPath = Files.createTempFile("test", ".xlsx");
            pdfPath = Files.createTempFile("output", ".pdf");

            // Mock the document converter to throw OfficeException
            when(documentConverter.convert(any(File.class))).thenReturn(sourceJob);
            when(sourceJob.to(any(File.class))).thenReturn(targetJob);
            doThrow(new OfficeException("Conversion failed")).when(targetJob).execute();

            // Execute and verify exception
            Path finalXlsPath = xlsPath;
            Path finalPdfPath = pdfPath;
            assertThrows(OfficeException.class, () ->
                pdfService.convertXlsToPdf(finalXlsPath, finalPdfPath)
            );

            // Verify the conversion was attempted
            verify(documentConverter).convert(xlsPath.toFile());
            verify(targetJob).execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            // Clean up
            try {
                if (xlsPath != null) Files.deleteIfExists(xlsPath);
                if (pdfPath != null) Files.deleteIfExists(pdfPath);
            } catch (IOException ignored) {
            }
        }
    }

    @Test
    void testConvertDocToPdf_Success() throws IOException, OfficeException {
        // Create temporary test files
        Path docPath = Files.createTempFile("test", ".docx");
        Path pdfPath = Files.createTempFile("output", ".pdf");

        try {
            // Mock the document converter chain
            when(documentConverter.convert(any(File.class))).thenReturn(sourceJob);
            when(sourceJob.to(any(File.class))).thenReturn(targetJob);
            doNothing().when(targetJob).execute();

            // Execute conversion
            pdfService.convertDocToPdf(docPath, pdfPath);

            // Verify the conversion chain was called
            verify(documentConverter).convert(docPath.toFile());
            verify(sourceJob).to(pdfPath.toFile());
            verify(targetJob).execute();
        } finally {
            // Clean up
            Files.deleteIfExists(docPath);
            Files.deleteIfExists(pdfPath);
        }
    }

    @Test
    void testConvertDocToPdf_OfficeException() throws OfficeException {
        // Create temporary test files
        Path docPath = null;
        Path pdfPath = null;

        try {
            docPath = Files.createTempFile("test", ".docx");
            pdfPath = Files.createTempFile("output", ".pdf");

            // Mock the document converter to throw OfficeException
            when(documentConverter.convert(any(File.class))).thenReturn(sourceJob);
            when(sourceJob.to(any(File.class))).thenReturn(targetJob);
            doThrow(new OfficeException("Conversion failed")).when(targetJob).execute();

            // Execute and verify exception
            Path finalDocPath = docPath;
            Path finalPdfPath = pdfPath;
            assertThrows(OfficeException.class, () ->
                pdfService.convertDocToPdf(finalDocPath, finalPdfPath)
            );

            // Verify the conversion was attempted
            verify(documentConverter).convert(docPath.toFile());
            verify(targetJob).execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            // Clean up
            try {
                if (docPath != null) Files.deleteIfExists(docPath);
                if (pdfPath != null) Files.deleteIfExists(pdfPath);
            } catch (IOException ignored) {
            }
        }
    }

    @Test
    void testConvertXlsToPdf_WithRealPaths() throws IOException, OfficeException {
        // Test with actual file system paths
        Path xlsPath = Path.of("C:\\test\\input.xlsx");
        Path pdfPath = Path.of("C:\\test\\output.pdf");

        // Mock the document converter chain
        when(documentConverter.convert(any(File.class))).thenReturn(sourceJob);
        when(sourceJob.to(any(File.class))).thenReturn(targetJob);
        doNothing().when(targetJob).execute();

        // Execute conversion
        pdfService.convertXlsToPdf(xlsPath, pdfPath);

        // Verify correct file objects were used
        verify(documentConverter).convert(xlsPath.toFile());
        verify(sourceJob).to(pdfPath.toFile());
        verify(targetJob).execute();
    }

    @Test
    void testConvertDocToPdf_WithRealPaths() throws IOException, OfficeException {
        // Test with actual file system paths
        Path docPath = Path.of("C:\\test\\input.docx");
        Path pdfPath = Path.of("C:\\test\\output.pdf");

        // Mock the document converter chain
        when(documentConverter.convert(any(File.class))).thenReturn(sourceJob);
        when(sourceJob.to(any(File.class))).thenReturn(targetJob);
        doNothing().when(targetJob).execute();

        // Execute conversion
        pdfService.convertDocToPdf(docPath, pdfPath);

        // Verify correct file objects were used
        verify(documentConverter).convert(docPath.toFile());
        verify(sourceJob).to(pdfPath.toFile());
        verify(targetJob).execute();
    }
}