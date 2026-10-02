package io.github.gflabandon.counselor.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class RequestErrorController implements ErrorController {
    @RequestMapping("/error")
    public ModelAndView error(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = code instanceof Integer value ? value : 404;
        return ErrorPages.page(request, status, Boolean.TRUE.equals(request.getAttribute("csrfFailure")));
    }
}
