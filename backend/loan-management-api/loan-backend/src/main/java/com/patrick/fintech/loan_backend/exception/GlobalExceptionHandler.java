package com.patrick.fintech.loan_backend.exception;

import com.patrick.fintech.loan_backend.exception.DuplicateBorrowerException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.AccessDeniedException;

import org.springframework.validation.FieldError;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ============================================================
    // DUPLICATE BORROWER
    // ============================================================

    @ExceptionHandler(DuplicateBorrowerException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateBorrower(
            DuplicateBorrowerException ex) {

        Map<String, Object> body = new LinkedHashMap<>();

        body.put(
                "timestamp",
                LocalDateTime.now().toString()
        );

        body.put(
                "error",
                ex.getMessage()
        );

        if (ex.getExistingBorrower() != null) {

            Map<String, Object> existing =
                    new LinkedHashMap<>();

            var borrower =
                    ex.getExistingBorrower();

            existing.put(
                    "id",
                    borrower.getId()
            );

            existing.put(
                    "firstName",
                    borrower.getFirstName()
            );

            existing.put(
                    "lastName",
                    borrower.getLastName()
            );

            existing.put(
                    "email",
                    borrower.getEmail()
            );

            existing.put(
                    "phone",
                    borrower.getPhone()
            );

            existing.put(
                    "matchedOn",
                    ex.getMatchedOn()
            );

            body.put(
                    "existingBorrower",
                    existing
            );
        }

        return json(
                HttpStatus.CONFLICT,
                body
        );
    }


    // ============================================================
    // VALIDATION
    // ============================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors =
                new LinkedHashMap<>();

        ex.getBindingResult()
                .getAllErrors()
                .forEach(error -> {

                    String field;

                    if (error instanceof FieldError fieldError) {
                        field = fieldError.getField();
                    } else {
                        field = "error";
                    }

                    errors.put(
                            field,
                            error.getDefaultMessage()
                    );
                });

        return json(
                HttpStatus.BAD_REQUEST,
                error(
                        "Validation failed",
                        errors
                )
        );
    }


    // ============================================================
    // ACCESS DENIED
    // ============================================================

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccess(
            AccessDeniedException ex) {

        log.warn(
                "Access denied: {}",
                ex.getMessage()
        );

        Map<String, Object> body =
                error(
                        "Access denied",
                        ex.getMessage()
                );

        body.put(
                "status",
                HttpStatus.FORBIDDEN.value()
        );

        return json(
                HttpStatus.FORBIDDEN,
                body
        );
    }


    // ============================================================
    // DATA INTEGRITY
    // ============================================================

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(
            DataIntegrityViolationException ex) {

        String cause =
                ex.getMostSpecificCause() != null
                        ? ex.getMostSpecificCause().getMessage()
                        : ex.getMessage();

        log.warn(
                "Data integrity violation: {}",
                cause
        );

        String friendly =
                "This action was already completed or conflicts with an existing record. "
                        + "Please refresh and try again.";

        return json(
                HttpStatus.BAD_REQUEST,
                error(
                        friendly,
                        null
                )
        );
    }


    // ============================================================
    // FILE TOO LARGE
    // ============================================================

    @ExceptionHandler(
            org.springframework.web.multipart.MaxUploadSizeExceededException.class
    )
    public ResponseEntity<Map<String, Object>> handleMaxUpload(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex) {

        log.warn(
                "Upload rejected — exceeds servlet max request size: {}",
                ex.getMessage()
        );

        return json(
                HttpStatus.BAD_REQUEST,
                error(
                        "File is too large for the server to accept. "
                                + "Please upload a smaller file.",
                        null
                )
        );
    }


    // ============================================================
    // RUNTIME / BUSINESS ERROR
    // ============================================================

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(
            RuntimeException ex) {

        log.warn(
                "Business error: {}",
                ex.getMessage()
        );

        String message =
                ex.getMessage() != null &&
                        !ex.getMessage().isBlank()
                        ? ex.getMessage()
                        : "The requested operation could not be completed.";

        return json(
                HttpStatus.BAD_REQUEST,
                error(
                        message,
                        null
                )
        );
    }


    // ============================================================
    // GENERAL ERROR
    // ============================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(
            Exception ex) {

        log.error(
                "Unhandled error",
                ex
        );

        return json(
                HttpStatus.INTERNAL_SERVER_ERROR,
                error(
                        "Internal server error",
                        null
                )
        );
    }


    // ============================================================
    // JSON RESPONSE
    // ============================================================

    private ResponseEntity<Map<String, Object>> json(
            HttpStatus status,
            Map<String, Object> body) {

        return ResponseEntity
                .status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }


    // ============================================================
    // ERROR BODY
    // ============================================================

    private Map<String, Object> error(
            String message,
            Object detail) {

        Map<String, Object> body =
                new LinkedHashMap<>();

        body.put(
                "timestamp",
                LocalDateTime.now().toString()
        );

        body.put(
                "success",
                false
        );

        body.put(
                "error",
                message
        );

        if (detail != null) {

            body.put(
                    "detail",
                    detail
            );
        }

        return body;
    }
}
