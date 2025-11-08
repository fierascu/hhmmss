package eu.hhmmss.app.uploadingfiles.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final UploadService uploadService;

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView handleMaxSizeException(MaxUploadSizeExceededException exc) {
        ModelAndView modelAndView = new ModelAndView("upload");
        modelAndView.addObject("errorMessage", "File size exceeds the maximum limit of 128KB.");

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
