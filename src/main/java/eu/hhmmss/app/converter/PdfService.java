package eu.hhmmss.app.converter;

import lombok.extern.slf4j.Slf4j;
import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.office.OfficeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Service for converting documents to PDF using LibreOffice in headless mode.
 * This provides 1:1 "print-to-PDF" conversion that preserves the exact visual appearance
 * of Excel and Word documents.
 */
@Service
@Slf4j
public class PdfService {

    private final DocumentConverter documentConverter;

    public PdfService(@Autowired(required = false) DocumentConverter documentConverter) {
        this.documentConverter = documentConverter;
        if (documentConverter == null) {
            log.warn("DocumentConverter is not available. PDF conversion features will be disabled.");
            log.warn("This usually means LibreOffice is not properly configured.");
            log.warn("Please check the application logs for LibreOffice configuration errors.");
        }
    }

    /**
     * Checks if PDF conversion is available (LibreOffice is configured).
     */
    public boolean isConversionAvailable() {
        return documentConverter != null;
    }

    /**
     * Converts an Excel (XLS/XLSX) file to PDF by "printing" it through LibreOffice.
     * This preserves the exact visual appearance as it would appear in Excel.
     *
     * @param xlsPath Path to the input Excel file
     * @param pdfPath Path where the output PDF should be saved
     * @throws IOException if file operations fail
     * @throws OfficeException if LibreOffice conversion fails or is not available
     */
    public void convertXlsToPdf(Path xlsPath, Path pdfPath) throws OfficeException {
        if (documentConverter == null) {
            log.error("Cannot convert XLS to PDF: LibreOffice is not available");
            throw new OfficeException("LibreOffice is not configured. PDF conversion is not available. " +
                    "Please check the application logs for configuration errors.");
        }
        log.info("Converting XLS to PDF: {} -> {}", xlsPath, pdfPath);
        documentConverter
                .convert(xlsPath.toFile())
                .to(pdfPath.toFile())
                .execute();
        log.info("XLS to PDF conversion completed successfully");
    }

    /**
     * Converts a Word (DOC/DOCX) file to PDF by "printing" it through LibreOffice.
     * This preserves the exact visual appearance as it would appear in Word.
     *
     * @param docPath Path to the input Word file
     * @param pdfPath Path where the output PDF should be saved
     * @throws IOException if file operations fail
     * @throws OfficeException if LibreOffice conversion fails or is not available
     */
    public void convertDocToPdf(Path docPath, Path pdfPath) throws IOException, OfficeException {
        if (documentConverter == null) {
            log.error("Cannot convert DOC to PDF: LibreOffice is not available");
            throw new OfficeException("LibreOffice is not configured. PDF conversion is not available. " +
                    "Please check the application logs for configuration errors.");
        }
        log.info("Converting DOC to PDF: {} -> {}", docPath, pdfPath);
        documentConverter
                .convert(docPath.toFile())
                .to(pdfPath.toFile())
                .execute();
        log.info("DOC to PDF conversion completed successfully");
    }
}
