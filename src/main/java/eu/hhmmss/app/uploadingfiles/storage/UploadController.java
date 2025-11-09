package eu.hhmmss.app.uploadingfiles.storage;

import eu.hhmmss.app.converter.DocService;
import eu.hhmmss.app.converter.HhmmssDto;
import eu.hhmmss.app.converter.PdfService;
import eu.hhmmss.app.converter.XlsService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final PdfService pdfService;

    @GetMapping("/")
    public String listUploadedFiles(Model model, HttpSession session) {
        // Check for upload error in session
        String uploadError = (String) session.getAttribute("uploadError");
        if (uploadError != null) {
            model.addAttribute("errorMessage", uploadError);
            session.removeAttribute("uploadError");
        }

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
                                   RedirectAttributes redirectAttributes) {
        try {
            // Step 1: Store the uploaded file
            String uuidFilename = uploadService.store(file);
            log.info("Stored uploaded file as: {}", uuidFilename);

            // Step 2: Parse the uploaded XLS file
            Path uploadedFilePath = uploadService.load(uuidFilename);
            HhmmssDto extractedData = XlsService.readTimesheet(uploadedFilePath);
            log.info("Extracted data from uploaded file: {} tasks, {} meta fields",
                    extractedData.getTasks().size(),
                    extractedData.getMeta().size());

            // Step 3: Generate DOCX with extracted data
            String extractedFilename = "timesheet_" + UUID.randomUUID() + ".docx";
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
            String xlsPdfFilename = "input_" + UUID.randomUUID() + ".pdf";
            Path xlsPdfPath = uploadService.load(xlsPdfFilename);
            pdfService.convertXlsToPdf(uploadedFilePath, xlsPdfPath);
            log.info("Generated PDF from input XLS as: {}", xlsPdfFilename);

            // Step 5: Generate PDF from output DOC (1:1 print)
            String docPdfFilename = "output_" + UUID.randomUUID() + ".pdf";
            Path docPdfPath = uploadService.load(docPdfFilename);
            pdfService.convertDocToPdf(extractedFilePath, docPdfPath);
            log.info("Generated PDF from output DOC as: {}", docPdfFilename);

            // Pass all filenames to the view
            redirectAttributes.addFlashAttribute("originalFilename", file.getOriginalFilename());
            redirectAttributes.addFlashAttribute("uuidFilename", uuidFilename);
            redirectAttributes.addFlashAttribute("extractedFilename", extractedFilename);
            redirectAttributes.addFlashAttribute("xlsPdfFilename", xlsPdfFilename);
            redirectAttributes.addFlashAttribute("docPdfFilename", docPdfFilename);
            redirectAttributes.addFlashAttribute("successMessage",
                    "File processed successfully! Extracted " + extractedData.getTasks().size() + " tasks and generated PDFs.");

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
        }

        return "redirect:/";
    }

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
        return ResponseEntity.notFound().build();
    }

}