package com.qinggan.travel.itinerary.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ItineraryResponse(
    String schemaVersion,
    String tripId,
    String tripName,
    LocalDate startDate,
    LocalDate endDate,
    int durationDays,
    long revision,
    Instant updatedAt,
    List<DayResponse> days
) {
}
