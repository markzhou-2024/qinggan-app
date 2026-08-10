package com.qinggan.travel.itinerary.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NavigationPointResponse(
    String id,
    String name,
    String address,
    String type,
    String note,
    String navigationKeyword,
    boolean isRecommended,
    String verificationStatus,
    GeoCoordinateResponse primaryCoordinate,
    List<GeoCoordinateResponse> alternateCoordinates
) {
}
