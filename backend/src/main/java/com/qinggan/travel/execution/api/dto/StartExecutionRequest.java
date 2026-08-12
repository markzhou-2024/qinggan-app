package com.qinggan.travel.execution.api.dto;

public record StartExecutionRequest(
    String requestId,
    long expectedRevision,
    String occurredAt
) {
}
