package com.universalimporter.api.common;

import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

/**
 * Every API error becomes an {@code application/problem+json} body carrying a {@code code} (design D4).
 * Framework errors keep their own status; business errors get the status mapped from their code.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> handleDomain(DomainException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ErrorHttpStatus.of(ex.code()), ex.getMessage());
        if (!ex.items().isEmpty()) {
            problem.setProperty("errors", ex.items());
        }
        return respond(problem, ex.code(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        // The details stay in the server log; the client only learns that something went wrong.
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error.");
        return respond(problem, ErrorCode.INTERNAL_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail detail ? detail : ProblemDetail.forStatus(statusCode);
        problem.setProperty("code", frameworkCode(ex, statusCode).name());
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }

    private static ErrorCode frameworkCode(Exception ex, HttpStatusCode statusCode) {
        if (ex instanceof MaxUploadSizeExceededException) {
            return ErrorCode.FILE_TOO_LARGE;
        }
        return statusCode.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.REQUEST_INVALID;
    }

    private static ResponseEntity<ProblemDetail> respond(
            ProblemDetail problem, ErrorCode code, HttpServletRequest request) {
        problem.setProperty("code", code.name());
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
