package com.patrick.fintech.loan_backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(com.patrick.fintech.loan_backend.exception.DuplicateBorrowerException.class)
    public ResponseEntity<Map<String,Object>> handleDuplicateBorrower(
            com.patrick.fintech.loan_backend.exception.DuplicateBorrowerException ex) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("error", ex.getMessage());
        Map<String,Object> existing = new LinkedHashMap<>();
        var b = ex.getExistingBorrower();
        existing.put("id", b.getId());
        existing.put("firstName", b.getFirstName());
        existing.put("lastName", b.getLastName());
        existing.put("email", b.getEmail());
        existing.put("phone", b.getPhone());
        existing.put("matchedOn", ex.getMatchedOn());
        body.put("existingBorrower", existing);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String,String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(e -> {
            String field = e instanceof FieldError fe ? fe.getField() : "error";
            errors.put(field, e.getDefaultMessage());
        });
        return bad("Validation failed", errors);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String,Object>> handleAccess(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(error("Access denied", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        String friendly = "This action was already completed or conflicts with an existing record. Please refresh and try again.";
        return ResponseEntity.badRequest().body(error(friendly, null));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String,Object>> handleMaxUpload(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        log.warn("Upload rejected — exceeds servlet max request size: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(error("File is too large for the server to accept. Please upload a smaller file.", null));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String,Object>> handleRuntime(RuntimeException ex) {
        log.warn("Business error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(error(ex.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> handleGeneral(Exception ex) {
        log.error("Unhandled error", ex);
        return ResponseEntity.status(500).body(error("Internal server error", null));
    }

    private ResponseEntity<Map<String,Object>> bad(String msg, Object detail) {
        return ResponseEntity.badRequest().body(error(msg, detail));
    }

    private Map<String,Object> error(String msg, Object detail) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("error", msg);
        if (detail != null) body.put("detail", detail);
        return body;
    }
}