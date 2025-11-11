package eu.hhmmss.app.uploadingfiles.storage;

import eu.hhmmss.app.converter.DocService;
import eu.hhmmss.app.converter.HhmmssDto;
import eu.hhmmss.app.converter.PdfService;
import eu.hhmmss.app.converter.XlsService;
import eu.hhmmss.app.converter.ZipProcessingService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jodconverter.core.office.OfficeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class UploadController {

    private final UploadService uploadService;
    private final ZipProcessingService zipProcessingService;
    private final PdfService pdfService;
    private final ThrottlingService throttlingService;

    @Value("${app.upload.max-xlsx-size}")
    private long maxXlsxSize;

    @Value("${app.upload.max-zip-size}")
    private long maxZipSize;

    @GetMapping("/")
    public String listUploadedFiles(@RequestParam(required = false) String theme,
                                    Model model, HttpSession session) {
        // Check for upload error in session
        String uploadError = (String) session.getAttribute("uploadError");
        if (uploadError != null) {
            model.addAttribute("errorMessage", uploadError);
            session.removeAttribute("uploadError");
        }

        // Set theme (default is terminal, classic is alternative)
        model.addAttribute("theme", "classic".equals(theme) ? "classic" : "terminal");

        model.addAttribute("files", uploadService.loadAll()
                .map(path -> MvcUriComponentsBuilder.fromMethodName(
                                UploadController.class, "serveFile",
                                path.getFileName().toString())
                        .build().toUri().toString())
                .toList());

        return "upload";
    }

    @GetMapping("/files/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {

        Resource file = uploadService.loadAsResource(filename);

        if (file == null) return ResponseEntity.notFound().build();

        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + file.getFilename() + "\"").body(file);
    }

    @PostMapping("/")
    public String handleFileUpload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) String theme,
                                   RedirectAttributes redirectAttributes) {
        // Acquire permit for throttling - throws TooManyRequestsException if unavailable
        throttlingService.acquirePermit();

        try {
            // Step 0: Validate file size based on file type
            validateFileSize(file);

            // Step 1: Store the uploaded file
            String uuidFilename = uploadService.store(file);
            log.info("Stored uploaded file as: {}", uuidFilename);

            Path uploadedFilePath = uploadService.load(uuidFilename);
            String originalFilename = file.getOriginalFilename();

            // Check if the file is a ZIP archive
            if (isZipFile(originalFilename)) {
                return handleZipFile(uploadedFilePath, uuidFilename, originalFilename, theme, redirectAttributes);
            } else {
                return handleExcelFile(uploadedFilePath, uuidFilename, originalFilename, theme, redirectAttributes);
            }

        } catch (StorageException e) {
            log.error("Storage error during file upload", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (IllegalStateException e) {
            log.error("Invalid file format", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid file format: " + e.getMessage());
        } catch (IOException e) {
            log.error("Error processing template", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error processing template: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during file processing", e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred: " + e.getMessage());
        } finally {
            // Always release the permit, even if an exception occurred
            throttlingService.releasePermit();
        }

        return buildRedirectUrl(theme);
    }

    private String buildRedirectUrl(String theme) {
        return "classic".equals(theme) ? "redirect:/?theme=classic" : "redirect:/";
    }

    /**
     * Handles processing of a single Excel file.
     */
    private String handleExcelFile(Path uploadedFilePath, String uuidFilename, String originalFilename,
                                    String theme, RedirectAttributes redirectAttributes) throws IOException, OfficeException {
        // Step 2: Parse the uploaded XLS file
        HhmmssDto extractedData = XlsService.readTimesheet(uploadedFilePath);
        log.info("Extracted data from uploaded file: {} tasks, {} meta fields",
                extractedData.getTasks().size(),
                extractedData.getMeta().size());

        // Step 3: Generate DOCX with extracted data
        // Maintain traceability: use input filename as base for output files
        String extractedFilename = uuidFilename + ".docx";
        Path extractedFilePath = uploadService.load(extractedFilename);

        // Copy template to temporary location
        Resource templateResource = new ClassPathResource("timesheet-template.docx");
        Path tempTemplate = Files.createTempFile("template-", ".docx");
        Files.copy(templateResource.getInputStream(), tempTemplate, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // Generate DOCX from template
        DocService.addDataToDocs(tempTemplate, extractedData, extractedFilePath);
        log.info("Generated timesheet DOCX file as: {}", extractedFilename);

        // Clean up temp template
        Files.deleteIfExists(tempTemplate);

        // Step 4: Generate PDF from input XLS (1:1 print)
        // Traceability: UUID-hash-originalExtension.pdf
        String xlsPdfFilename = uuidFilename + ".pdf";
        Path xlsPdfPath = uploadService.load(xlsPdfFilename);
        pdfService.convertXlsToPdf(uploadedFilePath, xlsPdfPath);
        log.info("Generated PDF from input XLS as: {}", xlsPdfFilename);

        // Step 5: Generate PDF from output DOC (1:1 print)
        // Traceability: UUID-hash-originalExtension.docx.pdf
        String docPdfFilename = extractedFilename + ".pdf";
        Path docPdfPath = uploadService.load(docPdfFilename);
        pdfService.convertDocToPdf(extractedFilePath, docPdfPath);
        log.info("Generated PDF from output DOC as: {}", docPdfFilename);

        // Build list of generated files for display
        java.util.List<String> generatedFiles = new java.util.ArrayList<>();
        generatedFiles.add(uuidFilename + " (Uploaded Excel)");
        generatedFiles.add(extractedFilename + " (Generated Timesheet DOCX)");
        generatedFiles.add(xlsPdfFilename + " (Input Excel as PDF)");
        generatedFiles.add(docPdfFilename + " (Timesheet as PDF)");

        // Build file URLs for download links
        java.util.List<String> generatedFileUrls = new java.util.ArrayList<>();
        generatedFileUrls.add(MvcUriComponentsBuilder.fromMethodName(
                UploadController.class, "serveFile", uuidFilename).build().toUri().toString());
        generatedFileUrls.add(MvcUriComponentsBuilder.fromMethodName(
                UploadController.class, "serveFile", extractedFilename).build().toUri().toString());
        generatedFileUrls.add(MvcUriComponentsBuilder.fromMethodName(
                UploadController.class, "serveFile", xlsPdfFilename).build().toUri().toString());
        generatedFileUrls.add(MvcUriComponentsBuilder.fromMethodName(
                UploadController.class, "serveFile", docPdfFilename).build().toUri().toString());

        // Pass data to the view
        redirectAttributes.addFlashAttribute("originalFilename", originalFilename);
        redirectAttributes.addFlashAttribute("uuidFilename", uuidFilename);
        redirectAttributes.addFlashAttribute("extractedFilename", extractedFilename);
        redirectAttributes.addFlashAttribute("xlsPdfFilename", xlsPdfFilename);
        redirectAttributes.addFlashAttribute("docPdfFilename", docPdfFilename);
        redirectAttributes.addFlashAttribute("generatedFiles", generatedFiles);
        redirectAttributes.addFlashAttribute("generatedFileUrls", generatedFileUrls);
        redirectAttributes.addFlashAttribute("isZipResult", false);
        redirectAttributes.addFlashAttribute("successMessage",
                "File processed successfully! Extracted " + extractedData.getTasks().size() + " tasks and generated PDFs.");

        return buildRedirectUrl(theme);
    }

    /**
     * Handles processing of a ZIP file containing multiple Excel files.
     */
    private String handleZipFile(Path zipFilePath, String uuidFilename, String originalFilename, String theme,
                                  RedirectAttributes redirectAttributes) throws IOException {
        log.info("Processing ZIP file: {}", originalFilename);

        // Prepare template
        Resource templateResource = new ClassPathResource("timesheet-template.docx");
        Path tempTemplate = Files.createTempFile("template-", ".docx");
        Files.copy(templateResource.getInputStream(), tempTemplate, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // Get output directory (same as upload directory)
        Path outputDir = zipFilePath.getParent();

        // Process ZIP file with traceability
        ZipProcessingService.ZipProcessingResult result = zipProcessingService.processZipFile(
                zipFilePath, uuidFilename, tempTemplate, outputDir
        );

        // Clean up temp template
        Files.deleteIfExists(tempTemplate);

        // Build success message
        StringBuilder message = new StringBuilder();
        message.append("ZIP file processed successfully! ");
        message.append("Converted ").append(result.successCount()).append(" file(s) to DOCX, Excel PDF, and Timesheet PDF.");

        if (result.failureCount() > 0) {
            message.append(" Failed: ").append(result.failureCount()).append(" file(s).");
        }

        // Build list of generated files for display
        java.util.List<String> generatedFiles = new java.util.ArrayList<>();
        generatedFiles.add(uuidFilename + " (Uploaded ZIP)");
        generatedFiles.add(result.resultZipFileName() + " (Converted Timesheets - DOCX + PDFs)");

        // Build file URLs for download links
        java.util.List<String> generatedFileUrls = new java.util.ArrayList<>();
        generatedFileUrls.add(MvcUriComponentsBuilder.fromMethodName(
                UploadController.class, "serveFile", uuidFilename).build().toUri().toString());
        generatedFileUrls.add(MvcUriComponentsBuilder.fromMethodName(
                UploadController.class, "serveFile", result.resultZipFileName()).build().toUri().toString());

        // Pass results to the view
        redirectAttributes.addFlashAttribute("originalFilename", originalFilename);
        redirectAttributes.addFlashAttribute("generatedFiles", generatedFiles);
        redirectAttributes.addFlashAttribute("generatedFileUrls", generatedFileUrls);
        redirectAttributes.addFlashAttribute("successMessage", message.toString());
        redirectAttributes.addFlashAttribute("isZipResult", true);
        redirectAttributes.addFlashAttribute("processedFiles", result.processedFiles());
        redirectAttributes.addFlashAttribute("failedFiles", result.failedFiles());

        return buildRedirectUrl(theme);
    }

    /**
     * Checks if a filename indicates a ZIP file.
     */
    private boolean isZipFile(String filename) {
        if (filename == null) return false;
        return filename.toLowerCase().endsWith(".zip");
    }

    /**
     * Validates that the uploaded file size does not exceed the limit for its file type.
     * XLSX files are limited to 128KB, ZIP files to 2MB.
     *
     * @param file the uploaded file to validate
     * @throws FileSizeExceededException if the file exceeds its type-specific size limit
     */
    private void validateFileSize(MultipartFile file) {
        String filename = file.getOriginalFilename();
        long fileSize = file.getSize();

        if (isZipFile(filename)) {
            // ZIP file validation
            if (fileSize > maxZipSize) {
                String maxSizeMB = String.format(java.util.Locale.US, "%.2f", maxZipSize / (1024.0 * 1024.0));
                String actualSizeMB = String.format(java.util.Locale.US, "%.2f", fileSize / (1024.0 * 1024.0));
                throw new FileSizeExceededException(
                        String.format("ZIP file size (%s MB) exceeds the maximum limit of %s MB",
                String maxSizeMB = String.format("%.2f", maxZipSize / (1024.0 * 1024.0));
                String actualSizeMB = String.format("%.2f", fileSize / (1024.0 * 1024.0));
                throw new FileSizeExceededException(
                        String.format("ZIP file size (%s MB) exceeds the maximum limit of %s MB.",
                                actualSizeMB, maxSizeMB));
            }
            log.debug("ZIP file size validation passed: {} bytes (max: {} bytes)", fileSize, maxZipSize);
        } else {
            // XLSX file validation
            if (fileSize > maxXlsxSize) {
                String maxSizeKB = String.format(java.util.Locale.US, "%.0f", maxXlsxSize / 1024.0);
                String actualSizeKB = String.format(java.util.Locale.US, "%.2f", fileSize / 1024.0);
                throw new FileSizeExceededException(
                        String.format("Excel file size (%s KB) exceeds the maximum limit of %s KB",
                String maxSizeKB = String.format("%.0f", maxXlsxSize / 1024.0);
                String actualSizeKB = String.format("%.2f", fileSize / 1024.0);
                throw new FileSizeExceededException(
                        String.format("Excel file size (%s KB) exceeds the maximum limit of %s KB.",
                                actualSizeKB, maxSizeKB));
            }
            log.debug("XLSX file size validation passed: {} bytes (max: {} bytes)", fileSize, maxXlsxSize);
        }
    }

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
        return ResponseEntity.notFound().build();
    }

}