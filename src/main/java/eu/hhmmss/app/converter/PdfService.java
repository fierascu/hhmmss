package eu.hhmmss.app.converter;

import com.spire.doc.Document;
import com.spire.doc.FileFormat;
import com.spire.xls.Workbook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Service for converting documents to PDF using Spire.XLS and Spire.Doc libraries.
 * This provides high-quality PDF conversion that preserves the exact visual appearance
 * of Excel and Word documents.
 *
 * Note: Using free versions which have the following limitations:
 * - Spire.XLS Free: Max 5 worksheets and 200 rows per worksheet
 * - Spire.Doc Free: Max 500 paragraphs and 25 tables, PDF output limited to 3 pages
 */
@Service
@Slf4j
public class PdfService {

    /**
     * Converts an Excel (XLS/XLSX) file to PDF using Spire.XLS.
     * This preserves the exact visual appearance as it would appear in Excel.
     *
     * @param xlsPath Path to the input Excel file
     * @param pdfPath Path where the output PDF should be saved
     * @throws IOException if file operations fail
     */
    public void convertXlsToPdf(Path xlsPath, Path pdfPath) throws IOException {
        log.info("Converting XLS to PDF using Spire.XLS: {} -> {}", xlsPath, pdfPath);
        try {
            Workbook workbook = new Workbook();
            workbook.loadFromFile(xlsPath.toString());
            workbook.saveToFile(pdfPath.toString(), com.spire.xls.FileFormat.PDF);
            log.info("XLS to PDF conversion completed successfully");
        } catch (Exception e) {
            log.error("Failed to convert XLS to PDF", e);
            throw new IOException("Failed to convert XLS to PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Converts a Word (DOC/DOCX) file to PDF using Spire.Doc.
     * This preserves the exact visual appearance as it would appear in Word.
     *
     * @param docPath Path to the input Word file
     * @param pdfPath Path where the output PDF should be saved
     * @throws IOException if file operations fail
     */
    public void convertDocToPdf(Path docPath, Path pdfPath) throws IOException {
        log.info("Converting DOC to PDF using Spire.Doc: {} -> {}", docPath, pdfPath);
        try {
            Document document = new Document();
            document.loadFromFile(docPath.toString());
            document.saveToFile(pdfPath.toString(), FileFormat.PDF);
            log.info("DOC to PDF conversion completed successfully");
        } catch (Exception e) {
            log.error("Failed to convert DOC to PDF", e);
            throw new IOException("Failed to convert DOC to PDF: " + e.getMessage(), e);
        }
    }
}
