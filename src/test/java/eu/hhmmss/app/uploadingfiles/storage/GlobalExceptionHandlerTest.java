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

        handler.handleMaxSizeException(exception, request, response);

        verify(session).setAttribute("uploadError", "File size exceeds the maximum limit of 128KB.");
        verify(response).sendRedirect("/");
    }

    @Test
    void testHandleMaxSizeExceptionWithContextPath() throws IOException {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(128000);

        when(request.getSession()).thenReturn(session);
        when(request.getContextPath()).thenReturn("/myapp");

        handler.handleMaxSizeException(exception, request, response);

        verify(session).setAttribute("uploadError", "File size exceeds the maximum limit of 128KB.");
        verify(response).sendRedirect("/myapp/");
    }

    @Test
    void testHandleMaxSizeExceptionSetsCorrectErrorMessage() throws IOException {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(200000);

        when(request.getSession()).thenReturn(session);
        when(request.getContextPath()).thenReturn("");

        handler.handleMaxSizeException(exception, request, response);

        // Verify the exact error message
        verify(session).setAttribute(eq("uploadError"), eq("File size exceeds the maximum limit of 128KB."));
    }

    @Test
    void testHandleMaxSizeExceptionCallsSessionAndResponse() throws IOException {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(128000);

        when(request.getSession()).thenReturn(session);
        when(request.getContextPath()).thenReturn("/app");

        handler.handleMaxSizeException(exception, request, response);

        // Verify that session.setAttribute is called exactly once
        verify(session, times(1)).setAttribute(anyString(), anyString());

        // Verify that response.sendRedirect is called exactly once
        verify(response, times(1)).sendRedirect(anyString());
    }
}
