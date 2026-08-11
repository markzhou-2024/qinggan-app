package com.qinggan.travel.family.application;

import com.qinggan.travel.family.domain.FamilyRole;

public record AuthenticatedDevice(
    Long tripDatabaseId,
    String tripCode,
    String deviceId,
    FamilyRole role,
    long bindingVersion
) {
}
