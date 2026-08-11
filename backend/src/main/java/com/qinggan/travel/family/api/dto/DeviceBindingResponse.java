package com.qinggan.travel.family.api.dto;

public record DeviceBindingResponse(
    String tripId,
    String role,
    String label,
    String deviceId,
    String deviceName,
    long bindingVersion,
    String deviceToken
) {
}
