package com.snackcounter.handler;

import com.snackcounter.exception.NotAllowedException;
import com.snackcounter.exception.RefillConflictException;
import com.snackcounter.exception.RefillNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns rule violations into clear HTTP answers (RFC 7807 problem details) instead of 500s.
 *
 * <p>The one place rejected requests are logged: 4xx as a single WARN line (an expected rule, no stack trace),
 * anything unexpected as ERROR with the stack trace.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({RefillNotFoundException.class, NoSuchElementException.class})
    public ProblemDetail notFound(RuntimeException e, HttpServletRequest request) {
        return rejected(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler({RefillConflictException.class, IllegalStateException.class})
    public ProblemDetail conflict(RuntimeException e, HttpServletRequest request) {
        return rejected(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    @ExceptionHandler(NotAllowedException.class)
    public ProblemDetail notAllowed(NotAllowedException e, HttpServletRequest request) {
        return rejected(HttpStatus.FORBIDDEN, e.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException e, HttpServletRequest request) {
        return rejected(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    /** Bean validation on request bodies: say which fields are wrong, e.g. "receiptImageBase64 must not be blank". */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalidRequest(MethodArgumentNotValidException e, HttpServletRequest request) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining(", "));
        return rejected(HttpStatus.BAD_REQUEST, detail, request);
    }

    /** Malformed JSON or an unknown value, such as an office other than OSLO or BENGALURU. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        return rejected(HttpStatus.BAD_REQUEST,
                "Unreadable request body: check the JSON and the values (office must be OSLO or BENGALURU)", request);
    }

    /** A path or query value of the wrong type, e.g. /api/refills/abc or ?month=November. */
    @ExceptionHandler(TypeMismatchException.class)
    public ProblemDetail wrongType(TypeMismatchException e, HttpServletRequest request) {
        return rejected(HttpStatus.BAD_REQUEST, "'" + e.getValue() + "' is not a valid " + e.getPropertyName(), request);
    }

    /**
     * Anything else. Spring's own errors (unknown URL, wrong HTTP method, ...) keep their status; a real bug
     * becomes a 500 that is logged with its stack trace and doesn't leak internals to the caller.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception e, HttpServletRequest request) {
        if (e instanceof ErrorResponse springError) {
            return rejected(HttpStatus.valueOf(springError.getStatusCode().value()), springError.getBody().getDetail(), request);
        }
        log.error("500 on {} {}", request.getMethod(), request.getRequestURI(), e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on our side. It has been logged.");
    }

    private static ProblemDetail rejected(HttpStatus status, String detail, HttpServletRequest request) {
        log.warn("{} {} on {} {}: {}", status.value(), status.getReasonPhrase(), request.getMethod(),
                request.getRequestURI(), detail);
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
