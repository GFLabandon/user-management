package io.github.gflabandon.counselor.web;

import io.github.gflabandon.counselor.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class RequestErrorAdvice {
    @ModelAttribute("requestId")
    public String requestId(HttpServletRequest request) { return ErrorPages.requestId(request); }

    @ExceptionHandler(BusinessException.class)
    public ModelAndView business(BusinessException failure, HttpServletRequest request) {
        int status = failure instanceof RecordNotFoundException ? 404 : failure instanceof EditConflictException ? 409 : 400;
        return ErrorPages.page(request, status, false).addObject("errorMessage", failure.getMessage());
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView tooLarge(HttpServletRequest request) { return ErrorPages.page(request, 413, false); }

    @ExceptionHandler({ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class, MultipartException.class})
    public ModelAndView invalid(HttpServletRequest request) { return ErrorPages.page(request, 400, false); }

    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView forbidden(HttpServletRequest request) { return ErrorPages.page(request, 403, false); }

    @ExceptionHandler(org.springframework.web.ErrorResponseException.class)
    public ModelAndView framework(org.springframework.web.ErrorResponseException failure, HttpServletRequest request) {
        return ErrorPages.page(request, failure.getStatusCode().value(), false);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView unexpected(Exception failure, HttpServletRequest request) {
        // Preserve framework statuses without exposing exception details or rejected input.
        if (failure instanceof org.springframework.web.ErrorResponse error && error.getStatusCode().is4xxClientError()) {
            return ErrorPages.page(request, error.getStatusCode().value(), false);
        }
        int status = failure instanceof DataAccessException ? 503 : 500;
        LoggerFactory.getLogger(RequestErrorAdvice.class).error("request_failed status={} type={}", status, failure.getClass().getSimpleName());
        return ErrorPages.page(request, status, false);
    }
}
