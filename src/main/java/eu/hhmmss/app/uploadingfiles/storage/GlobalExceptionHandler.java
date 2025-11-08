package eu.hhmmss.app.uploadingfiles.storage;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public void handleMaxSizeException(MaxUploadSizeExceededException exc,
                                       HttpServletRequest request,
                                       HttpServletResponse response) throws IOException {
        log.info("Handling MaxUploadSizeExceededException");

        // Store error in session
        request.getSession().setAttribute("uploadError", "File size exceeds the maximum limit of 128KB.");

        // Send redirect
        response.sendRedirect(request.getContextPath() + "/");
    }
}
