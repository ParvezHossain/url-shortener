package com.parvez.urlshortener.exception;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain and validation exceptions to RFC 7807 {@link ProblemDetail}
 * responses. See docs/ARCHITECTURE.md §6 for the status-code table.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Reports invalid destination, alias, or expiry input as a bad request. */
    @ExceptionHandler(InvalidUrlException.class)
    public ProblemDetail handleInvalidUrl(InvalidUrlException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Reports a claimed alias as a conflict. */
    @ExceptionHandler(DuplicateAliasException.class)
    public ProblemDetail handleDuplicateAlias(DuplicateAliasException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Reports an unknown short code as not found. */
    @ExceptionHandler(UrlNotFoundException.class)
    public ProblemDetail handleUrlNotFound(UrlNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Reports an expired link as gone. */
    @ExceptionHandler(UrlExpiredException.class)
    public ProblemDetail handleUrlExpired(UrlExpiredException ex) {
        return problem(HttpStatus.GONE, ex.getMessage());
    }

    /** Reports field validation messages without including rejected input values. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList());
        return problem;
    }

    /** Reports unreadable JSON as a client input error without exposing parser details. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableRequest(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
    }

    /** Returns a generic server error without exposing exception details. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }
    private ProblemDetail problem(HttpStatus status, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        return problem;
    }

}
