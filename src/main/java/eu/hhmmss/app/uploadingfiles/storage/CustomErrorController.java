package eu.hhmmss.app.uploadingfiles.storage;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

@Controller
@RequiredArgsConstructor
@Slf4j
public class CustomErrorController implements ErrorController {

    private final UploadService uploadService;

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        log.info("CustomErrorController: handling error, exception type: {}",
                exception != null ? exception.getClass().getName() : "null");

        // Check if it's a MaxUploadSizeExceededException
        if (exception instanceof MaxUploadSizeExceededException) {
            model.addAttribute("errorMessage", "File size exceeds the maximum limit of 128KB.");
        } else if (exception instanceof Exception) {
            model.addAttribute("errorMessage", "An error occurred: " + ((Exception) exception).getMessage());
        }

        // Add the list of files (same as in listUploadedFiles)
        try {
            model.addAttribute("files", uploadService.loadAll()
                    .map(path -> MvcUriComponentsBuilder.fromMethodName(
                                    UploadController.class, "serveFile",
                                    path.getFileName().toString())
                            .build().toUri().toString())
                    .toList());
        } catch (Exception e) {
            log.error("Error loading files list", e);
            model.addAttribute("files", java.util.Collections.emptyList());
        }

        return "upload";
    }
}
