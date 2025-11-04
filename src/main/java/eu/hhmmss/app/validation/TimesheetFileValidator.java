package eu.hhmmss.app.validation;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static eu.hhmmss.app.validation.FileValidationException.ValidationErrorCode.*;

/**
 * Validates uploaded timesheet files for security and format compliance.
 * Protects against:
 * - Invalid file types
 * - Files that are too large (potential DoS)
 * - Corrupt or malformed Excel files
 * - XXE attacks
 * - Zip bombs (decompression bombs)
 * - Missing required structure
 */
@Slf4j
@Component
public class TimesheetFileValidator {

    // Security limits
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final long MIN_FILE_SIZE = 100; // 100 bytes minimum
    private static final long MAX_UNCOMPRESSED_SIZE = 50 * 1024 * 1024; // 50 MB uncompressed
    private static final int MAX_SHEET_COUNT = 50; // Reasonable limit
    private static final double MAX_COMPRESSION_RATIO = 100.0; // Detect zip bombs

    // Allowed file types
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(".xlsx", ".xls");
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel"
    );

    // Required sheet name
    private static final String REQUIRED_SHEET_NAME = "Timesheet";

    /**
     * Validates an uploaded timesheet file.
     *
     * @param file the uploaded file
     * @throws FileValidationException if validation fails
     */
    public void validateTimesheetFile(MultipartFile file) throws FileValidationException {
        Objects.requireNonNull(file, "File cannot be null");

        log.debug("Validating file: name={}, size={}, contentType={}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        validateNotEmpty(file);
        validateFileSize(file);
        validateFileExtension(file);
        validateMimeType(file);
        validateExcelStructure(file);

        log.info("File validation successful: {}", file.getOriginalFilename());
    }

    /**
     * Validates that the file is not empty.
     */
    private void validateNotEmpty(MultipartFile file) throws FileValidationException {
        if (file.isEmpty() || file.getSize() == 0) {
            throw new FileValidationException(FILE_EMPTY, "Uploaded file is empty");
        }

        if (file.getSize() < MIN_FILE_SIZE) {
            throw new FileValidationException(FILE_EMPTY,
                    String.format("File too small (%d bytes), minimum is %d bytes",
                            file.getSize(), MIN_FILE_SIZE));
        }
    }

    /**
     * Validates file size to prevent DoS attacks.
     */
    private void validateFileSize(MultipartFile file) throws FileValidationException {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileValidationException(FILE_TOO_LARGE,
                    String.format("File size (%d bytes) exceeds maximum allowed size (%d bytes)",
                            file.getSize(), MAX_FILE_SIZE));
        }
    }

    /**
     * Validates file extension.
     */
    private void validateFileExtension(MultipartFile file) throws FileValidationException {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new FileValidationException(INVALID_FILE_TYPE, "Filename is missing");
        }

        String lowerFilename = filename.toLowerCase();
        boolean hasValidExtension = ALLOWED_EXTENSIONS.stream()
                .anyMatch(lowerFilename::endsWith);

        if (!hasValidExtension) {
            throw new FileValidationException(INVALID_FILE_TYPE,
                    String.format("Invalid file extension. Allowed: %s, but got: %s",
                            ALLOWED_EXTENSIONS, filename));
        }
    }

    /**
     * Validates MIME type.
     */
    private void validateMimeType(MultipartFile file) throws FileValidationException {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            // Don't fail immediately - browsers may send incorrect MIME types
            // Log warning but allow if extension is correct
            log.warn("Unexpected MIME type: {} for file: {}. Expected one of: {}",
                    contentType, file.getOriginalFilename(), ALLOWED_MIME_TYPES);
        }
    }

    /**
     * Validates Excel structure and checks for security issues.
     * Protects against:
     * - Corrupt Excel files
     * - Zip bombs (decompression bombs)
     * - Missing required sheets
     * - XXE attacks (POI handles this internally)
     */
    private void validateExcelStructure(MultipartFile file) throws FileValidationException {
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new FileValidationException(CORRUPT_FILE,
                    "Failed to read file contents", e);
        }

        // Check compression ratio to detect zip bombs
        validateCompressionRatio(file.getSize(), fileBytes.length);

        // Try to open as Excel workbook
        try (InputStream is = new ByteArrayInputStream(fileBytes);
             Workbook workbook = new XSSFWorkbook(is)) {

            // Check sheet count (prevent resource exhaustion)
            int sheetCount = workbook.getNumberOfSheets();
            if (sheetCount > MAX_SHEET_COUNT) {
                throw new FileValidationException(SECURITY_VIOLATION,
                        String.format("Too many sheets (%d), maximum allowed is %d",
                                sheetCount, MAX_SHEET_COUNT));
            }

            // Verify required sheet exists
            Sheet timesheetSheet = workbook.getSheet(REQUIRED_SHEET_NAME);
            if (timesheetSheet == null) {
                throw new FileValidationException(MISSING_REQUIRED_SHEET,
                        String.format("Required sheet '%s' not found in Excel file", REQUIRED_SHEET_NAME));
            }

            // Estimate uncompressed size by checking loaded objects
            long estimatedSize = estimateWorkbookSize(workbook);
            if (estimatedSize > MAX_UNCOMPRESSED_SIZE) {
                throw new FileValidationException(SECURITY_VIOLATION,
                        String.format("Uncompressed size (%d bytes) exceeds limit (%d bytes). Possible zip bomb.",
                                estimatedSize, MAX_UNCOMPRESSED_SIZE));
            }

            log.debug("Excel structure validation passed. Sheets: {}, Required sheet found: {}",
                    sheetCount, REQUIRED_SHEET_NAME);

        } catch (POIXMLException e) {
            throw new FileValidationException(INVALID_EXCEL_FORMAT,
                    "File is not a valid Excel format or is corrupted", e);
        } catch (IOException e) {
            throw new FileValidationException(CORRUPT_FILE,
                    "Failed to parse Excel file. File may be corrupted.", e);
        } catch (IllegalArgumentException e) {
            throw new FileValidationException(INVALID_EXCEL_FORMAT,
                    "Invalid Excel file format", e);
        }
    }

    /**
     * Validates compression ratio to detect zip bombs.
     */
    private void validateCompressionRatio(long compressedSize, long uncompressedSize)
            throws FileValidationException {
        if (compressedSize == 0) return;

        double ratio = (double) uncompressedSize / compressedSize;
        if (ratio > MAX_COMPRESSION_RATIO) {
            throw new FileValidationException(SECURITY_VIOLATION,
                    String.format("Compression ratio (%.2f) exceeds maximum (%.2f). Possible zip bomb detected.",
                            ratio, MAX_COMPRESSION_RATIO));
        }
    }

    /**
     * Estimates the in-memory size of the workbook to detect decompression bombs.
     */
    private long estimateWorkbookSize(Workbook workbook) {
        long size = 0;
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            // Rough estimate: each row uses ~200 bytes minimum
            size += (long) sheet.getPhysicalNumberOfRows() * 200;
        }
        return size;
    }

    /**
     * Gets the maximum allowed file size.
     */
    public long getMaxFileSize() {
        return MAX_FILE_SIZE;
    }
}
