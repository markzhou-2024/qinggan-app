package com.qinggan.travel.family.api;

import com.qinggan.travel.family.api.dto.BindDeviceRequest;
import com.qinggan.travel.family.api.dto.DeviceBindingResponse;
import com.qinggan.travel.family.api.dto.FamilyRolesResponse;
import com.qinggan.travel.family.api.dto.TakeoverDeviceRequest;
import com.qinggan.travel.family.application.FamilyBindingService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/family")
public class FamilyBindingController {

    private final FamilyBindingService service;

    public FamilyBindingController(FamilyBindingService service) {
        this.service = service;
    }

    @GetMapping("/roles")
    public FamilyRolesResponse roles(
        @PathVariable String tripId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return service.roles(tripId, authorization);
    }

    @PostMapping("/devices/bind")
    public DeviceBindingResponse bind(
        @PathVariable String tripId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody BindDeviceRequest request
    ) {
        return service.bind(tripId, authorization, request);
    }

    @PostMapping("/devices/takeover")
    public DeviceBindingResponse takeover(
        @PathVariable String tripId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody TakeoverDeviceRequest request
    ) {
        return service.takeover(tripId, authorization, request);
    }

    @GetMapping("/me")
    public DeviceBindingResponse me(
        @PathVariable String tripId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return service.currentBinding(tripId, authorization);
    }
}
