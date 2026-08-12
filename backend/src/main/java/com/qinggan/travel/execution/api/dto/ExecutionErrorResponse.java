package com.qinggan.travel.execution.api.dto;

public record ExecutionErrorResponse(
    String code,
    String message,
    ExecutionSnapshotResponse latest
) {
}
