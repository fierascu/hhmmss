package eu.hhmmss.app.uploadingfiles.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @GetMapping("/")
    public String listUploadedFiles(Model model) {

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

        String uuidFilename = uploadService.store(file);
        redirectAttributes.addFlashAttribute("originalFilename", file.getOriginalFilename());
        redirectAttributes.addFlashAttribute("uuidFilename", uuidFilename);

        return "redirect:/";
    }

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
        return ResponseEntity.notFound().build();
    }

}