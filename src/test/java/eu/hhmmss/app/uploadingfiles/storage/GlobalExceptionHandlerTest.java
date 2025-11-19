package eu.hhmmss.app.uploadingfiles.storage;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;

import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpSession session;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new GlobalExceptionHandler();
    }

    @Test
    void testHandleMaxSizeException() throws IOException {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(128000);

        when(request.getSession()).thenReturn(session);
        when(request.getContextPath()).thenReturn("");
        when(request.getParameter("theme")).thenReturn(null);

        handler.handleMaxSizeException(exception, request, response);

        verify(session).setAttribute("uploadError", "File size exceeds the maximum limit of 2MB.");
        verify(response).sendRedirect("/");
    }

    @Test
    void testHandleMaxSizeExceptionWithContextPath() throws IOException {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(128000);

        when(request.getSession()).thenReturn(session);
        when(request.getContextPath()).thenReturn("/myapp");
        when(request.getParameter("theme")).thenReturn(null);

        handler.handleMaxSizeException(exception, request, response);

        verify(session).setAttribute("uploadError", "File size exceeds the maximum limit of 2MB.");
        verify(response).sendRedirect("/myapp/");
    }

    @Test
    void testHandleMaxSizeExceptionSetsCorrectErrorMessage() throws IOException {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(200000);

        when(request.getSession()).thenReturn(session);
        when(request.getContextPath()).thenReturn("");
        when(request.getParameter("theme")).thenReturn(null);

        handler.handleMaxSizeException(exception, request, response);

        // Verify the exact error message
        verify(session).setAttribute(eq("uploadError"), eq("File size exceeds the maximum limit of 2MB."));
    }

    @Test
    void testHandleMaxSizeExceptionCallsSessionAndResponse() throws IOException {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(128000);

        when(request.getSession()).thenReturn(session);
        when(request.getContextPath()).thenReturn("/app");
        when(request.getParameter("theme")).thenReturn(null);

        handler.handleMaxSizeException(exception, request, response);

        // Verify that session.setAttribute is called exactly once
        verify(session, times(1)).setAttribute(anyString(), anyString());

        // Verify that response.sendRedirect is called exactly once
        verify(response, times(1)).sendRedirect(anyString());
    }

    @Test
    void testHandleMaxSizeExceptionPreservesTerminalTheme() throws IOException {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(128000);

        when(request.getSession()).thenReturn(session);
        when(request.getContextPath()).thenReturn("");
        when(request.getParameter("theme")).thenReturn("terminal");

        handler.handleMaxSizeException(exception, request, response);

        verify(session).setAttribute("uploadError", "File size exceeds the maximum limit of 2MB.");
        verify(response).sendRedirect("/?theme=terminal");
    }

    @Test
    void testHandleMaxSizeExceptionPreservesClassicTheme() throws IOException {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(128000);

        when(request.getSession()).thenReturn(session);
        when(request.getContextPath()).thenReturn("");
        when(request.getParameter("theme")).thenReturn("classic");

        handler.handleMaxSizeException(exception, request, response);

        verify(session).setAttribute("uploadError", "File size exceeds the maximum limit of 2MB.");
        verify(response).sendRedirect("/?theme=classic");
    }

    @Test
    void testHandleTooManyRequests() throws IOException {
        TooManyRequestsException exception = new TooManyRequestsException("Server is busy");

        when(request.getSession()).thenReturn(session);
        when(request.getContextPath()).thenReturn("");
        when(request.getParameter("theme")).thenReturn(null);

        handler.handleTooManyRequests(exception, request, response);

        verify(session).setAttribute("uploadError", "Server is busy");
        verify(response).sendRedirect("/");
    }

    @Test
    void testHandleTooManyRequestsWithContextPath() throws IOException {
        TooManyRequestsException exception = new TooManyRequestsException("Too many concurrent requests");

        when(request.getSession()).thenReturn(session);
        when(request.getContextPath()).thenReturn("/myapp");
        when(request.getParameter("theme")).thenReturn(null);

        handler.handleTooManyRequests(exception, request, response);

        verify(session).setAttribute("uploadError", "Too many concurrent requests");
        verify(response).sendRedirect("/myapp/");
    }

    @Test
    void testHandleTooManyRequestsPreservesTheme() throws IOException {
        TooManyRequestsException exception = new TooManyRequestsException("Server is busy");

        when(request.getSession()).thenReturn(session);
        when(request.getContextPath()).thenReturn("");
        when(request.getParameter("theme")).thenReturn("terminal");

        handler.handleTooManyRequests(exception, request, response);

        verify(session).setAttribute("uploadError", "Server is busy");
        verify(response).sendRedirect("/?theme=terminal");
    }

    @Test
    void testHandleFileSizeExceeded() throws IOException {
        FileSizeExceededException exception = new FileSizeExceededException("Excel file size (250.00 KB) exceeds the maximum limit of 200 KB.");

        when(request.getSession()).thenReturn(session);
        when(request.getContextPath()).thenReturn("");
        when(request.getParameter("theme")).thenReturn(null);

        handler.handleFileSizeExceeded(exception, request, response);

        verify(session).setAttribute("uploadError", "Excel file size (250.00 KB) exceeds the maximum limit of 200 KB.");
        verify(response).sendRedirect("/");
    }

    @Test
    void testHandleFileSizeExceededForZip() throws IOException {
        FileSizeExceededException exception = new FileSizeExceededException("ZIP file size (3.50 MB) exceeds the maximum limit of 2.00 MB.");

        when(request.getSession()).thenReturn(session);
        when(request.getContextPath()).thenReturn("/app");
        when(request.getParameter("theme")).thenReturn(null);

        handler.handleFileSizeExceeded(exception, request, response);

        verify(session).setAttribute("uploadError", "ZIP file size (3.50 MB) exceeds the maximum limit of 2.00 MB.");
        verify(response).sendRedirect("/app/");
    }

    @Test
    void testHandleFileSizeExceededCallsSessionAndResponse() throws IOException {
        FileSizeExceededException exception = new FileSizeExceededException("File too large");

        when(request.getSession()).thenReturn(session);
        when(request.getContextPath()).thenReturn("");
        when(request.getParameter("theme")).thenReturn(null);

        handler.handleFileSizeExceeded(exception, request, response);

        // Verify that session.setAttribute is called exactly once
        verify(session, times(1)).setAttribute(anyString(), anyString());

        // Verify that response.sendRedirect is called exactly once
        verify(response, times(1)).sendRedirect(anyString());
    }

    @Test
    void testHandleFileSizeExceededPreservesTheme() throws IOException {
        FileSizeExceededException exception = new FileSizeExceededException("File too large");

        when(request.getSession()).thenReturn(session);
        when(request.getContextPath()).thenReturn("");
        when(request.getParameter("theme")).thenReturn("classic");

        handler.handleFileSizeExceeded(exception, request, response);

        verify(session).setAttribute("uploadError", "File too large");
        verify(response).sendRedirect("/?theme=classic");
    }
}
