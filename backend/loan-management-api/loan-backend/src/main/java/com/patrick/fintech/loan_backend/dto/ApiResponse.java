package com.patrick.fintech.loan_backend.dto;

import lombok.Data;

@Data
public class ApiResponse<T> {
    private boolean success;
    private String  message;
    private T       data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true; r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true; r.message = message; r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> ok(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true; r.message = message;
        return r;
    }

    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false; r.message = message;
        return r;
    }
}