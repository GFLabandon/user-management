package io.github.gflabandon.counselor.interceptor;

import java.io.IOException;

import io.github.gflabandon.counselor.controller.LoginController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoginInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        if (request.getSession().getAttribute(LoginController.SESSION_USER_KEY) != null) {
            return true;
        }

        log.debug("Unauthenticated request to {}", request.getRequestURI());
        response.sendRedirect(request.getContextPath() + "/login");
        return false;
    }
}
