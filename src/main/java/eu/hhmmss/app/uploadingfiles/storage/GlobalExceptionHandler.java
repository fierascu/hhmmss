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
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Global exception handler with error message sanitization.
 *
 * Security Features:
 * - Generates unique error reference IDs for tracking
 * - Logs detailed errors server-side with reference ID
 * - Shows only generic, sanitized messages to users
 * - Prevents information disclosure through error messages
 *
 * Best Practice:
 * Never expose internal error details (file paths, stack traces, SQL queries, etc.)
 * to end users. Always log detailed errors server-side and provide users with
 * a reference ID they can share with support.
 */
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

        // Preserve theme parameter in redirect
        String theme = request.getParameter("theme");
        String redirectUrl = buildRedirectUrl(request.getContextPath(), theme);

        // Send redirect
        response.sendRedirect(redirectUrl);
    }

    @ExceptionHandler(FileSizeExceededException.class)
    public void handleFileSizeExceeded(FileSizeExceededException exc,
                                       HttpServletRequest request,
                                       HttpServletResponse response) throws IOException {
        log.warn("Handling FileSizeExceededException: {}", exc.getMessage());

        // Store error in session
        request.getSession().setAttribute("uploadError", exc.getMessage());

        // Preserve theme parameter in redirect
        String theme = request.getParameter("theme");
        String redirectUrl = buildRedirectUrl(request.getContextPath(), theme);

        // Send redirect
        response.sendRedirect(redirectUrl);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView handleMaxSizeException(MaxUploadSizeExceededException exc,
                                                HttpServletRequest request) {
        log.warn("File upload rejected: size exceeds 2MB limit - Remote: {}",
                 request.getRemoteAddr());

        // Check if request accepts HTML (browser) or is API call
        String acceptHeader = request.getHeader("Accept");
        boolean isApiCall = acceptHeader != null &&
                           (acceptHeader.contains("application/json") ||
                            acceptHeader.contains("*/*") && !acceptHeader.contains("text/html"));

        // For API calls (Postman, curl, etc.), return error page with 413 status
        // For browser calls, return 200 with error page to prevent browser confusion
        HttpStatus status = isApiCall ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.OK;

        // Get theme parameter
        String theme = request.getParameter("theme");
        if (theme == null) {
            theme = "ascii";
        }

        // Return to upload page with error message
        ModelAndView mav = new ModelAndView("upload");
        mav.addObject("errorMessage", "File size exceeds the maximum limit of 2MB.");
        mav.addObject("theme", theme);
        mav.setStatus(status);

        return mav;
    }

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ModelAndView handleStorageFileNotFound(StorageFileNotFoundException exc, HttpServletRequest request) {
        // Generate unique error reference ID
        String errorId = generateErrorId();

        // Log detailed error server-side with reference ID
        log.error("[Error ID: {}] File not found: {} - Path: {}",
                errorId, exc.getMessage(), request.getRequestURI(), exc);

        // Return sanitized error message to user
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", HttpStatus.NOT_FOUND.value());
        mav.addObject("error", HttpStatus.NOT_FOUND.getReasonPhrase());
        mav.addObject("message", "The requested file was not found. Reference ID: " + errorId);
        mav.addObject("timestamp", LocalDateTime.now());
        mav.addObject("path", sanitizePath(request.getRequestURI()));
        mav.setStatus(HttpStatus.NOT_FOUND);

        return mav;
    }

    @ExceptionHandler(StorageException.class)
    public ModelAndView handleStorageException(StorageException exc, HttpServletRequest request) {
        // Generate unique error reference ID
        String errorId = generateErrorId();

        // Log detailed error server-side with reference ID
        log.error("[Error ID: {}] Storage error: {} - Path: {}",
                errorId, exc.getMessage(), request.getRequestURI(), exc);

        // Return sanitized error message to user
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        mav.addObject("error", HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        mav.addObject("message", "A file storage error occurred. Please try again. Reference ID: " + errorId);
        mav.addObject("timestamp", LocalDateTime.now());
        mav.addObject("path", sanitizePath(request.getRequestURI()));
        mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        return mav;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public void handleNoResourceFound(NoResourceFoundException exc,
                                      HttpServletRequest request,
                                      HttpServletResponse response) throws IOException {
        // Log the event at debug level (this is expected behavior for jsessionid URLs)
        log.debug("NoResourceFoundException: {} - Path: {}", exc.getMessage(), request.getRequestURI());

        // Preserve theme parameter in redirect
        String theme = request.getParameter("theme");
        String redirectUrl = buildRedirectUrl(request.getContextPath(), theme);

        // Send redirect to home page with theme preserved
        response.sendRedirect(redirectUrl);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleGenericException(Exception exc, HttpServletRequest request) {
        // Generate unique error reference ID
        String errorId = generateErrorId();

        // Log detailed error server-side with reference ID
        log.error("[Error ID: {}] Unexpected error occurred: {} - Type: {} - Path: {}",
                errorId, exc.getMessage(), exc.getClass().getName(), request.getRequestURI(), exc);

        // Return sanitized error message to user (no exception details)
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        mav.addObject("error", HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        mav.addObject("message", "An unexpected error occurred. Please try again or contact support. Reference ID: " + errorId);
        mav.addObject("timestamp", LocalDateTime.now());
        mav.addObject("path", sanitizePath(request.getRequestURI()));
        mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        return mav;
    }

    /**
     * Generates a unique error reference ID for tracking and support purposes.
     * Uses UUID v4 for guaranteed uniqueness.
     *
     * @return a unique error reference ID
     */
    private String generateErrorId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Sanitizes request path to prevent information disclosure.
     * Removes sensitive information while keeping useful context.
     *
     * @param path the request URI path
     * @return sanitized path
     */
    private String sanitizePath(String path) {
        if (path == null) {
            return "/";
        }

        // Remove query parameters that might contain sensitive data
        int queryStart = path.indexOf('?');
        if (queryStart > 0) {
            path = path.substring(0, queryStart);
        }

        // Remove session IDs or tokens from path if present
        path = path.replaceAll("[a-f0-9]{32,}", "[ID]"); // Replace long hex strings
        path = path.replaceAll("[A-Za-z0-9]{20,}", "[TOKEN]"); // Replace long alphanumeric strings

        return path;
    }

    /**
     * Builds a redirect URL preserving the theme parameter if present.
     *
     * @param contextPath the servlet context path
     * @param theme the theme parameter value (may be null)
     * @return the redirect URL with theme parameter if applicable
     */
    private String buildRedirectUrl(String contextPath, String theme) {
        if ("classic".equals(theme)) {
            return contextPath + "/?theme=classic";
        } else if ("terminal".equals(theme)) {
            return contextPath + "/?theme=terminal";
        } else {
            return contextPath + "/"; // Default to ASCII
        }
    }
}
