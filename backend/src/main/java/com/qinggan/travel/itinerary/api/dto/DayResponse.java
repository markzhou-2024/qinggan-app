package com.qinggan.travel.itinerary.api.dto;

import java.time.LocalDate;
import java.util.List;

public record DayResponse(
    String id,
    int number,
    LocalDate date,
    String title,
    String type,
    Integer plannedDistanceKm,
    String plannedDistance,
    Integer plannedDrivingMinutes,
    String plannedDrivingDuration,
    PlaceResponse origin,
    PlaceResponse destination,
    List<StopResponse> stops,
    StayResponse stay
) {
}
