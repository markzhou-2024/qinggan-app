package com.qinggan.travel.execution.application;

import com.qinggan.travel.execution.api.dto.ExecutionSnapshotResponse;
import org.springframework.http.HttpStatus;

public final class ExecutionException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final ExecutionSnapshotResponse latest;

    public ExecutionException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ExecutionException(HttpStatus status, String code, String message,
                              ExecutionSnapshotResponse latest) {
        super(message);
        this.status = status;
        this.code = code;
        this.latest = latest;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public ExecutionSnapshotResponse getLatest() { return latest; }
}
