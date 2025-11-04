package eu.hhmmss.app.controller;

import eu.hhmmss.app.converter.DocService;
import eu.hhmmss.app.converter.HhmmssDto;
import eu.hhmmss.app.converter.XlsService;
import eu.hhmmss.app.validation.FileValidationException;
import eu.hhmmss.app.validation.TimesheetFileValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for timesheet conversion operations.
 * Handles file upload, validation, and conversion from Excel to Word format.
 */
@Slf4j
@RestController
@RequestMapping("/api/timesheet")
@RequiredArgsConstructor
public class TimesheetController {

    private final TimesheetFileValidator fileValidator;

    /**
     * Converts an uploaded Excel timesheet to a Word document.
     *
     * @param file the Excel file to convert
     * @return the generated Word document
     */
    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Resource> convertTimesheet(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "template", required = false) MultipartFile template) {

        log.info("Received conversion request for file: {}", file.getOriginalFilename());

        try {
            // Validate uploaded file
            fileValidator.validateTimesheetFile(file);

            // Save uploaded file temporarily
            Path tempXlsx = Files.createTempFile("timesheet-", ".xlsx");
            file.transferTo(tempXlsx.toFile());

            try {
                // Parse Excel file
                HhmmssDto timesheetData = XlsService.readTimesheet(tempXlsx);

                // Determine template to use
                Path templatePath;
                if (template != null && !template.isEmpty()) {
                    // Validate template file
                    fileValidator.validateTimesheetFile(template);
                    templatePath = Files.createTempFile("template-", ".docx");
                    template.transferTo(templatePath.toFile());
                } else {
                    // Use default template (needs to be provided)
                    throw new IllegalStateException("Default template not configured. Please provide a template file.");
                }

                try {
                    // Generate Word document
                    Path outputDocx = Files.createTempFile("timesheet-output-", ".docx");
                    DocService.addDataToDocs(templatePath, timesheetData, outputDocx);

                    // Prepare response
                    Resource resource = new FileSystemResource(outputDocx.toFile());
                    String filename = "timesheet-" + System.currentTimeMillis() + ".docx";

                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .body(resource);

                } finally {
                    // Clean up template if it was uploaded
                    if (template != null && !template.isEmpty()) {
                        Files.deleteIfExists(templatePath);
                    }
                }
            } finally {
                // Clean up temp Excel file
                Files.deleteIfExists(tempXlsx);
            }

        } catch (FileValidationException e) {
            log.warn("File validation failed: {} - {}", e.getErrorCode(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(null);
        } catch (IOException e) {
            log.error("IO error during conversion", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        } catch (Exception e) {
            log.error("Unexpected error during conversion", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Timesheet Converter");
        return ResponseEntity.ok(response);
    }

    /**
     * Get upload requirements/limits.
     */
    @GetMapping("/upload-info")
    public ResponseEntity<Map<String, Object>> getUploadInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("maxFileSizeMB", fileValidator.getMaxFileSize() / (1024.0 * 1024.0));
        info.put("allowedExtensions", new String[]{".xlsx", ".xls"});
        info.put("requiredSheet", "Timesheet");
        return ResponseEntity.ok(info);
    }

    /**
     * Exception handler for validation errors.
     */
    @ExceptionHandler(FileValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(FileValidationException ex) {
        log.warn("Validation error: {} - {}", ex.getErrorCode(), ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getErrorCode().name());
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Generic exception handler.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        Map<String, String> error = new HashMap<>();
        error.put("error", "INTERNAL_ERROR");
        error.put("message", "An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
