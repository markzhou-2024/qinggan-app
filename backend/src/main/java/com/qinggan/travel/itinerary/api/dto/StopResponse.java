package com.qinggan.travel.itinerary.api.dto;

import java.util.List;

public record StopResponse(
    String id,
    int sequence,
    String type,
    String priority,
    boolean optional,
    String status,
    PlaceResponse place,
    NavigationPointResponse recommendedNavigationPoint,
    List<NavigationPointResponse> alternativeNavigationPoints
) {
}
