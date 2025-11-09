package eu.hhmmss.app.uploadingfiles.storage;

import eu.hhmmss.app.converter.HhmmssDto;
import eu.hhmmss.app.converter.XlsGeneratorService;
import eu.hhmmss.app.converter.XlsService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Path;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class UploadController {

    private final UploadService uploadService;
    private final XlsGeneratorService xlsGeneratorService;

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

            // Step 3: Generate new XLS with extracted data
            String extractedFilename = "extracted_" + UUID.randomUUID() + ".xlsx";
            Path extractedFilePath = uploadService.load(extractedFilename);
            xlsGeneratorService.generateExtractedDataXls(extractedData, extractedFilePath);
            log.info("Generated extracted data file as: {}", extractedFilename);

            // Pass both filenames to the view
            redirectAttributes.addFlashAttribute("originalFilename", file.getOriginalFilename());
            redirectAttributes.addFlashAttribute("uuidFilename", uuidFilename);
            redirectAttributes.addFlashAttribute("extractedFilename", extractedFilename);
            redirectAttributes.addFlashAttribute("successMessage",
                    "File processed successfully! Extracted " + extractedData.getTasks().size() + " tasks.");

        } catch (StorageException e) {
            log.error("Storage error during file upload", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (IllegalStateException e) {
            log.error("Invalid file format", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid file format: " + e.getMessage());
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