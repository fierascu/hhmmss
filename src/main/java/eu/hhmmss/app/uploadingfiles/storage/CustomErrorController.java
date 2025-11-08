package eu.hhmmss.app.uploadingfiles.storage;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

@Controller
@RequiredArgsConstructor
public class CustomErrorController implements ErrorController {

    private final UploadService uploadService;

    @RequestMapping("/error")
    public ModelAndView handleError(HttpServletRequest request) {
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        ModelAndView modelAndView = new ModelAndView("upload");

        // Check if it's a MaxUploadSizeExceededException
        if (exception instanceof MaxUploadSizeExceededException) {
            modelAndView.addObject("errorMessage", "File size exceeds the maximum limit of 128KB.");
        } else {
            // Handle other errors
            String errorMessage = "An error occurred during file upload.";
            if (exception instanceof Exception) {
                errorMessage = ((Exception) exception).getMessage();
            }
            modelAndView.addObject("errorMessage", errorMessage);
        }

        // Add the list of files (same as in listUploadedFiles)
        modelAndView.addObject("files", uploadService.loadAll()
                .map(path -> MvcUriComponentsBuilder.fromMethodName(
                                UploadController.class, "serveFile",
                                path.getFileName().toString())
                        .build().toUri().toString())
                .toList());

        return modelAndView;
    }
}
