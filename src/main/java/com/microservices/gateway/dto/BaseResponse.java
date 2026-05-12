package com.microservices.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseResponse<T> {
    private final boolean success;
    private final int status;
    private final String message;
    private final T data;
    private final String error;
    private final String path;
    private final String timestamp;

    private BaseResponse(boolean success, int status, String message, T data, String error, String path, String timestamp) {
        this.success = success;
        this.status = status;
        this.message = message;
        this.data = data;
        this.error = error;
        this.path = path;
        this.timestamp = timestamp;
    }

    public static BaseResponse<Void> error(int status, String error, String message, String path) {
        return new BaseResponse<>(false, status, message, null, error, path, Instant.now().toString());
    }

    public boolean isSuccess() {
        return success;
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getError() {
        return error;
    }

    public String getPath() {
        return path;
    }

    public String getTimestamp() {
        return timestamp;
    }
}
