package com.qinggan.travel.family.api.dto;

import java.util.List;

public record FamilyRolesResponse(String tripId, List<FamilyRoleOptionResponse> roles) {
}
