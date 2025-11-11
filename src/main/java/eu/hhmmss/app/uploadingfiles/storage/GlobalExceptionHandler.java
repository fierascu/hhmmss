package eu.hhmmss.app.uploadingfiles.storage;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.time.LocalDateTime;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(TooManyRequestsException.class)
    public void handleTooManyRequests(TooManyRequestsException exc,
                                      HttpServletRequest request,
                                      HttpServletResponse response) throws IOException {
        log.warn("Handling TooManyRequestsException: {}", exc.getMessage());

        // Store error in session
        request.getSession().setAttribute("uploadError", exc.getMessage());

        // Send redirect
        response.sendRedirect(request.getContextPath() + "/");
    }

    @ExceptionHandler(FileSizeExceededException.class)
    public void handleFileSizeExceeded(FileSizeExceededException exc,
                                       HttpServletRequest request,
                                       HttpServletResponse response) throws IOException {
        log.warn("Handling FileSizeExceededException: {}", exc.getMessage());

        // Store error in session
        request.getSession().setAttribute("uploadError", exc.getMessage());

        // Send redirect
        response.sendRedirect(request.getContextPath() + "/");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public void handleMaxSizeException(MaxUploadSizeExceededException exc,
                                       HttpServletRequest request,
                                       HttpServletResponse response) throws IOException {
        log.info("Handling MaxUploadSizeExceededException");

        // Store error in session
        request.getSession().setAttribute("uploadError", "File size exceeds the maximum limit of 2MB.");

        // Send redirect
        response.sendRedirect(request.getContextPath() + "/");
    }

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ModelAndView handleStorageFileNotFound(StorageFileNotFoundException exc, HttpServletRequest request) {
        log.error("File not found: {}", exc.getMessage());

        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", HttpStatus.NOT_FOUND.value());
        mav.addObject("error", HttpStatus.NOT_FOUND.getReasonPhrase());
        mav.addObject("message", exc.getMessage());
        mav.addObject("timestamp", LocalDateTime.now());
        mav.addObject("path", request.getRequestURI());
        mav.setStatus(HttpStatus.NOT_FOUND);

        return mav;
    }

    @ExceptionHandler(StorageException.class)
    public ModelAndView handleStorageException(StorageException exc, HttpServletRequest request) {
        log.error("Storage error: {}", exc.getMessage(), exc);

        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        mav.addObject("error", HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        mav.addObject("message", exc.getMessage());
        mav.addObject("timestamp", LocalDateTime.now());
        mav.addObject("path", request.getRequestURI());
        mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        return mav;
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleGenericException(Exception exc, HttpServletRequest request) {
        log.error("Unexpected error occurred: {}", exc.getMessage(), exc);

        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        mav.addObject("error", HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        mav.addObject("message", "An unexpected error occurred: " + exc.getMessage());
        mav.addObject("timestamp", LocalDateTime.now());
        mav.addObject("path", request.getRequestURI());
        mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        return mav;
    }
}
