package com.qinggan.travel.family.api.dto;

import java.time.Instant;

public record FamilyRoleOptionResponse(
    String role,
    String label,
    String bindingStatus,
    String boundDeviceDisplayName,
    Instant boundAt,
    long bindingVersion
) {
}
