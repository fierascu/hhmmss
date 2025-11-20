package eu.hhmmss.app.uploadingfiles.storage;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
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
    void testHandleMaxSizeException() {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(128000);

        when(request.getParameter("theme")).thenReturn(null);

        ModelAndView mav = handler.handleMaxSizeException(exception, request);

        assertEquals("upload", mav.getViewName());
        assertEquals("File size exceeds the maximum limit of 2MB.", mav.getModel().get("errorMessage"));
        assertEquals("ascii", mav.getModel().get("theme"));
    }

    @Test
    void testHandleMaxSizeExceptionWithTerminalTheme() {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(128000);

        when(request.getParameter("theme")).thenReturn("terminal");

        ModelAndView mav = handler.handleMaxSizeException(exception, request);

        assertEquals("upload", mav.getViewName());
        assertEquals("File size exceeds the maximum limit of 2MB.", mav.getModel().get("errorMessage"));
        assertEquals("terminal", mav.getModel().get("theme"));
    }

    @Test
    void testHandleMaxSizeExceptionWithClassicTheme() {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(200000);

        when(request.getParameter("theme")).thenReturn("classic");

        ModelAndView mav = handler.handleMaxSizeException(exception, request);

        // Verify the exact error message and theme
        assertEquals("upload", mav.getViewName());
        assertEquals("File size exceeds the maximum limit of 2MB.", mav.getModel().get("errorMessage"));
        assertEquals("classic", mav.getModel().get("theme"));
    }

    @Test
    void testHandleMaxSizeExceptionReturnsCorrectStatusForBrowser() {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(128000);

        when(request.getParameter("theme")).thenReturn(null);
        when(request.getHeader("Accept")).thenReturn("text/html,application/xhtml+xml");

        ModelAndView mav = handler.handleMaxSizeException(exception, request);

        // Verify that the response status is OK (200) for browsers to prevent confusion
        assertEquals(HttpStatus.OK, mav.getStatus());
        assertEquals("upload", mav.getViewName());
    }

    @Test
    void testHandleMaxSizeExceptionReturnsCorrectStatusForApiCall() {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(128000);

        when(request.getParameter("theme")).thenReturn(null);
        when(request.getHeader("Accept")).thenReturn("application/json");

        ModelAndView mav = handler.handleMaxSizeException(exception, request);

        // Verify that the response status is 413 (Payload Too Large) for API calls
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, mav.getStatus());
        assertEquals("upload", mav.getViewName());
        assertEquals("File size exceeds the maximum limit of 2MB.", mav.getModel().get("errorMessage"));
    }

    @Test
    void testHandleMaxSizeExceptionDetectsPostmanCall() {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(128000);

        when(request.getParameter("theme")).thenReturn(null);
        when(request.getHeader("Accept")).thenReturn("*/*"); // Typical Postman/curl header

        ModelAndView mav = handler.handleMaxSizeException(exception, request);

        // Postman with */* should get 413 status
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, mav.getStatus());
        assertEquals("upload", mav.getViewName());
    }

    @Test
    void testHandleMaxSizeExceptionDefaultsToAsciiTheme() {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(128000);

        when(request.getParameter("theme")).thenReturn(null);

        ModelAndView mav = handler.handleMaxSizeException(exception, request);

        assertEquals("upload", mav.getViewName());
        assertEquals("ascii", mav.getModel().get("theme"));
        assertEquals("File size exceeds the maximum limit of 2MB.", mav.getModel().get("errorMessage"));
    }

    @Test
    void testHandleMaxSizeExceptionReturnsUploadView() {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(128000);

        when(request.getParameter("theme")).thenReturn("terminal");

        ModelAndView mav = handler.handleMaxSizeException(exception, request);

        assertEquals("upload", mav.getViewName());
        assertNotNull(mav.getModel().get("errorMessage"));
        assertNotNull(mav.getModel().get("theme"));
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

    @Test
    void testHandleNoResourceFoundWithoutTheme() throws IOException {
        NoResourceFoundException exception = mock(NoResourceFoundException.class);

        when(request.getContextPath()).thenReturn("");
        when(request.getParameter("theme")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/;jsessionid=ABC123");

        handler.handleNoResourceFound(exception, request, response);

        verify(response).sendRedirect("/");
    }

    @Test
    void testHandleNoResourceFoundWithTerminalTheme() throws IOException {
        NoResourceFoundException exception = mock(NoResourceFoundException.class);

        when(request.getContextPath()).thenReturn("");
        when(request.getParameter("theme")).thenReturn("terminal");
        when(request.getRequestURI()).thenReturn("/;jsessionid=ABC123");

        handler.handleNoResourceFound(exception, request, response);

        verify(response).sendRedirect("/?theme=terminal");
    }

    @Test
    void testHandleNoResourceFoundWithClassicTheme() throws IOException {
        NoResourceFoundException exception = mock(NoResourceFoundException.class);

        when(request.getContextPath()).thenReturn("");
        when(request.getParameter("theme")).thenReturn("classic");
        when(request.getRequestURI()).thenReturn("/;jsessionid=ABC123");

        handler.handleNoResourceFound(exception, request, response);

        verify(response).sendRedirect("/?theme=classic");
    }

    @Test
    void testHandleNoResourceFoundWithContextPath() throws IOException {
        NoResourceFoundException exception = mock(NoResourceFoundException.class);

        when(request.getContextPath()).thenReturn("/myapp");
        when(request.getParameter("theme")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/myapp/;jsessionid=ABC123");

        handler.handleNoResourceFound(exception, request, response);

        verify(response).sendRedirect("/myapp/");
    }

    @Test
    void testHandleNoResourceFoundWithContextPathAndTheme() throws IOException {
        NoResourceFoundException exception = mock(NoResourceFoundException.class);

        when(request.getContextPath()).thenReturn("/app");
        when(request.getParameter("theme")).thenReturn("terminal");
        when(request.getRequestURI()).thenReturn("/app/;jsessionid=XYZ789");

        handler.handleNoResourceFound(exception, request, response);

        verify(response).sendRedirect("/app/?theme=terminal");
    }
}
