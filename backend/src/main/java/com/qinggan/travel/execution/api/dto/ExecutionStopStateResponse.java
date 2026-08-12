package com.qinggan.travel.execution.api.dto;

import com.qinggan.travel.trip.domain.StopStatus;
import java.time.Instant;

public record ExecutionStopStateResponse(
    String stopId,
    StopStatus status,
    String updatedByRole,
    String updatedByDeviceId,
    Instant updatedAt
) {
}
