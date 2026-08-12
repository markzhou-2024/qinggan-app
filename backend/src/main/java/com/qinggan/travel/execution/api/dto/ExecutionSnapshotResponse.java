package com.qinggan.travel.execution.api.dto;

import com.qinggan.travel.trip.domain.TripStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ExecutionSnapshotResponse(
    String schemaVersion,
    String tripId,
    long revision,
    TripStatus status,
    LocalDate actualStartDate,
    List<ExecutionStopStateResponse> stopStates,
    Instant updatedAt
) {
}
