package com.qinggan.travel.family.api;

import org.springframework.http.HttpStatus;

public final class FamilyApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public FamilyApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
