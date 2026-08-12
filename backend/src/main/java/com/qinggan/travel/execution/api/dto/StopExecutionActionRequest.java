package com.qinggan.travel.execution.api.dto;

public record StopExecutionActionRequest(
    String requestId,
    long expectedRevision,
    String action,
    String occurredAt
) {
}
