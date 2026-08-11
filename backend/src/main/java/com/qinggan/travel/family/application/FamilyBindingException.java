package com.qinggan.travel.family.application;

import com.qinggan.travel.family.api.dto.FamilyRolesResponse;
import org.springframework.http.HttpStatus;

public final class FamilyBindingException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final FamilyRolesResponse latestRoles;

    public FamilyBindingException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public FamilyBindingException(
        HttpStatus status,
        String code,
        String message,
        FamilyRolesResponse latestRoles
    ) {
        super(message);
        this.status = status;
        this.code = code;
        this.latestRoles = latestRoles;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public FamilyRolesResponse getLatestRoles() {
        return latestRoles;
    }
}
