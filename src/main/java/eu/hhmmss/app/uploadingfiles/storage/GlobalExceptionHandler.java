package eu.hhmmss.app.uploadingfiles.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

import java.util.stream.Collectors;

@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final UploadService uploadService;

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView handleMaxSizeException(MaxUploadSizeExceededException exc) {
        log.info("Handling MaxUploadSizeExceededException");

        ModelAndView modelAndView = new ModelAndView("upload");
        modelAndView.addObject("errorMessage", "File size exceeds the maximum limit of 128KB.");

        // Load files as simple strings without using MvcUriComponentsBuilder during exception handling
        try {
            modelAndView.addObject("files", uploadService.loadAll()
                    .map(path -> "/files/" + path.getFileName().toString())
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            log.error("Error loading files during exception handling", e);
            modelAndView.addObject("files", java.util.Collections.emptyList());
        }

        return modelAndView;
    }
}
