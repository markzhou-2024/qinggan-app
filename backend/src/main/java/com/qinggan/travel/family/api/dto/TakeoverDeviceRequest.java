package com.qinggan.travel.family.api.dto;

public record TakeoverDeviceRequest(
    String requestId,
    String deviceId,
    String role,
    String deviceName,
    boolean confirmed
) {
}
