package com.qinggan.travel.itinerary.api.dto;

public record StayResponse(
    String hotelName,
    String address,
    String phone,
    String checkInNote,
    String parkingNote,
    String verificationStatus
) {
}
