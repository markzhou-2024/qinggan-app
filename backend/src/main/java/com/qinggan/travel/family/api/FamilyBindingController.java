package com.qinggan.travel.family.api;

import com.qinggan.travel.family.application.FamilyBindingService;
import com.qinggan.travel.family.application.FamilyBindingService.BindingCommand;
import com.qinggan.travel.family.application.FamilyBindingService.BindingResult;
import com.qinggan.travel.family.application.FamilyBindingService.DeviceIdentity;
import com.qinggan.travel.family.application.FamilyBindingService.RolesResult;
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

    public static final String JOIN_TOKEN_HEADER = "X-QingGan-Join-Token";

    private final FamilyBindingService service;

    public FamilyBindingController(FamilyBindingService service) {
        this.service = service;
    }

    @GetMapping("/roles")
    public RolesResult roles(
        @PathVariable String tripId,
        @RequestHeader(value = JOIN_TOKEN_HEADER, required = false) String joinToken
    ) {
        return service.listRoles(tripId, joinToken);
    }

    @PostMapping("/bind")
    public BindingResult bind(
        @PathVariable String tripId,
        @RequestHeader(value = JOIN_TOKEN_HEADER, required = false) String joinToken,
        @RequestBody BindRequest request
    ) {
        return service.bind(tripId, joinToken, request.toCommand());
    }

    @PostMapping("/takeover")
    public BindingResult takeover(
        @PathVariable String tripId,
        @RequestHeader(value = JOIN_TOKEN_HEADER, required = false) String joinToken,
        @RequestBody BindRequest request
    ) {
        return service.takeover(tripId, joinToken, request.toCommand());
    }

    @GetMapping("/me")
    public DeviceIdentity me(
        @PathVariable String tripId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return service.authenticate(tripId, authorization);
    }

    public record BindRequest(String requestId, String role, String deviceId, String deviceName) {
        BindingCommand toCommand() {
            return new BindingCommand(requestId, role, deviceId, deviceName);
        }
    }
}
