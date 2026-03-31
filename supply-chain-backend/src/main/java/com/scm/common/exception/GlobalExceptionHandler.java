package com.scm.common.exception;

import com.scm.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Maps common exceptions to {@link Result} and appropriate HTTP status codes.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        log.warn("Business exception [{}]: {}", e.getCode(), e.getMessage());
        HttpStatus status = resolveHttpStatus(e.getCode());
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        if (message.isEmpty()) {
            message = "Validation failed";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(Result.BAD_REQUEST, message));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        String msg = e.getMessage() != null && !e.getMessage().isEmpty() ? e.getMessage() : "Access denied";
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.fail(Result.FORBIDDEN, msg));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Result<Void>> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail(Result.UNAUTHORIZED, "用户名或密码错误"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(Result.FAIL, "Internal server error"));
    }

    private HttpStatus resolveHttpStatus(int code) {
        HttpStatus status = HttpStatus.resolve(code);
        if (status == null || status.is1xxInformational() || status.is2xxSuccessful() || status.is3xxRedirection()) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return status;
    }

    private String formatFieldError(FieldError err) {
        return err.getField() + ": " + err.getDefaultMessage();
    }
}
