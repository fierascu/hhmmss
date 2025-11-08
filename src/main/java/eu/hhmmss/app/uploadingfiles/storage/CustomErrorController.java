package eu.hhmmss.app.uploadingfiles.storage;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Controller
@RequiredArgsConstructor
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, HttpSession session) {
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        // Check if it's a MaxUploadSizeExceededException
        if (exception instanceof MaxUploadSizeExceededException) {
            session.setAttribute("errorMessage", "File size exceeds the maximum limit of 128KB.");
        } else if (exception instanceof Exception) {
            session.setAttribute("errorMessage", "An error occurred: " + ((Exception) exception).getMessage());
        }

        return "redirect:/";
    }
}
