
package com.patrick.fintech.loan_backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

   

    @ExceptionHandler(DuplicateBorrowerException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateBorrower(
            DuplicateBorrowerException ex) {

        Map<String, Object> body =
                new LinkedHashMap<>();

        body.put(
                "timestamp",
                LocalDateTime.now().toString()
        );

        body.put(
                "error",
                ex.getMessage()
        );

        Map<String, Object> existing =
                new LinkedHashMap<>();

        var b =
                ex.getExistingBorrower();

        if (b != null) {

            existing.put("id", b.getId());
            existing.put("firstName", b.getFirstName());
            existing.put("lastName", b.getLastName());
            existing.put("email", b.getEmail());
            existing.put("phone", b.getPhone());
        }

        existing.put(
                "matchedOn",
                ex.getMatchedOn()
        );

        body.put(
                "existingBorrower",
                existing
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(body);
    }


    // ============================================================
    // VALIDATION
    // ============================================================

    @ExceptionHandler(
            MethodArgumentNotValidException.class
    )
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors =
                new LinkedHashMap<>();

        ex.getBindingResult()
                .getAllErrors()
                .forEach(error -> {

                    String field =
                            error instanceof FieldError fe
                                    ? fe.getField()
                                    : "error";

                    errors.put(
                            field,
                            error.getDefaultMessage()
                    );
                });

        return bad(
                "Validation failed",
                errors
        );
    }


    // ============================================================
    // ACCESS DENIED
    // ============================================================

    @ExceptionHandler(
            AccessDeniedException.class
    )
    public ResponseEntity<Map<String, Object>> handleAccess(
            AccessDeniedException ex) {

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(
                        error(
                                "Access denied",
                                ex.getMessage()
                        )
                );
    }


    // ============================================================
    // DATA INTEGRITY
    // ============================================================

    @ExceptionHandler(
            DataIntegrityViolationException.class
    )
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(
            DataIntegrityViolationException ex) {

        log.error(
                "Data integrity violation",
                ex
        );

        String friendly =
                "This action conflicts with existing data. "
                        + "Please refresh the page and try again.";

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
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
            org.springframework.web.multipart
                    .MaxUploadSizeExceededException.class
    )
    public ResponseEntity<Map<String, Object>> handleMaxUpload(
            org.springframework.web.multipart
                    .MaxUploadSizeExceededException ex) {

        log.warn(
                "Upload rejected: {}",
                ex.getMessage()
        );

        return ResponseEntity
                .badRequest()
                .body(
                        error(
                                "File is too large for the server to accept. "
                                        + "Please upload a smaller file.",
                                null
                        )
                );
    }


    // ============================================================
    // ILLEGAL ARGUMENT
    // ============================================================

    @ExceptionHandler(
            IllegalArgumentException.class
    )
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {

        log.warn(
                "Bad request: {}",
                ex.getMessage()
        );

        return ResponseEntity
                .badRequest()
                .body(
                        error(
                                ex.getMessage(),
                                null
                        )
                );
    }


    // ============================================================
    // ILLEGAL STATE / BUSINESS STATE
    // ============================================================

    @ExceptionHandler(
            IllegalStateException.class
    )
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException ex) {

        log.error(
                "Application state error",
                ex
        );

        return ResponseEntity
                .status(
                        HttpStatus.INTERNAL_SERVER_ERROR
                )
                .body(
                        error(
                                "The server could not complete this operation.",
                                null
                        )
                );
    }


    // ============================================================
    // OTHER RUNTIME EXCEPTIONS
    // ============================================================

    @ExceptionHandler(
            RuntimeException.class
    )
    public ResponseEntity<Map<String, Object>> handleRuntime(
            RuntimeException ex) {

        /*
         * IMPORTANT:
         *
         * Do not hide programming/database errors as HTTP 400.
         * Log the complete stack trace so production debugging
         * shows the real cause.
         */
        log.error(
                "Unhandled runtime exception",
                ex
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        error(
                                "Internal server error",
                                null
                        )
                );
    }


    // ============================================================
    // EVERYTHING ELSE
    // ============================================================

    @ExceptionHandler(
            Exception.class
    )
    public ResponseEntity<Map<String, Object>> handleGeneral(
            Exception ex) {

        log.error(
                "Unhandled error",
                ex
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        error(
                                "Internal server error",
                                null
                        )
                );
    }


    // ============================================================
    // HELPERS
    // ============================================================

    private ResponseEntity<Map<String, Object>> bad(
            String message,
            Object detail) {

        return ResponseEntity
                .badRequest()
                .body(
                        error(
                                message,
                                detail
                        )
                );
    }


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
