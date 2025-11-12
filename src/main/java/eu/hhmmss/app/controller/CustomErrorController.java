package eu.hhmmss.app.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(@RequestParam(required = false) String theme,
                              HttpServletRequest request,
                              Model model) {
        // Set theme (default is ascii, other options: terminal, classic)
        String selectedTheme = theme != null ? theme : "ascii";
        if (!"ascii".equals(selectedTheme) && !"terminal".equals(selectedTheme) && !"classic".equals(selectedTheme)) {
            selectedTheme = "ascii";
        }
        model.addAttribute("theme", selectedTheme);

        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        if (status != null) {
            model.addAttribute("status", status.toString());
        }

        if (message != null) {
            model.addAttribute("message", message.toString());
        } else if (exception != null) {
            model.addAttribute("message", exception.toString());
        }

        model.addAttribute("path", request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));
        model.addAttribute("error", request.getAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE));
        model.addAttribute("timestamp", new java.util.Date());

        return "error";
    }
}
