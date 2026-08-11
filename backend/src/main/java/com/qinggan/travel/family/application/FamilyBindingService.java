package com.qinggan.travel.family.application;

import com.qinggan.travel.family.api.dto.BindDeviceRequest;
import com.qinggan.travel.family.api.dto.DeviceBindingResponse;
import com.qinggan.travel.family.api.dto.FamilyRoleOptionResponse;
import com.qinggan.travel.family.api.dto.FamilyRolesResponse;
import com.qinggan.travel.family.api.dto.TakeoverDeviceRequest;
import com.qinggan.travel.family.domain.BindingActionType;
import com.qinggan.travel.family.domain.DeviceStatus;
import com.qinggan.travel.family.domain.FamilyRole;
import com.qinggan.travel.family.domain.FamilyRoleBinding;
import com.qinggan.travel.family.domain.TripDevice;
import com.qinggan.travel.family.domain.TripDeviceBindingAction;
import com.qinggan.travel.family.persistence.FamilyRoleBindingJpaRepository;
import com.qinggan.travel.family.persistence.FamilyTripJpaRepository;
import com.qinggan.travel.family.persistence.TripDeviceBindingActionJpaRepository;
import com.qinggan.travel.family.persistence.TripDeviceJpaRepository;
import com.qinggan.travel.family.security.DeviceTokenService;
import com.qinggan.travel.family.security.FamilyJoinTokenVerifier;
import com.qinggan.travel.family.security.TokenHashingService;
import com.qinggan.travel.trip.domain.Trip;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FamilyBindingService {

    private final FamilyTripJpaRepository tripRepository;
    private final FamilyRoleBindingJpaRepository roleBindingRepository;
    private final TripDeviceJpaRepository deviceRepository;
    private final TripDeviceBindingActionJpaRepository bindingActionRepository;
    private final DeviceTokenService deviceTokenService;
    private final TokenHashingService tokenHashingService;
    private final FamilyJoinTokenVerifier joinTokenVerifier;
    private final DeviceAuthorizationService deviceAuthorizationService;

    public FamilyBindingService(
        FamilyTripJpaRepository tripRepository,
        FamilyRoleBindingJpaRepository roleBindingRepository,
        TripDeviceJpaRepository deviceRepository,
        TripDeviceBindingActionJpaRepository bindingActionRepository,
        DeviceTokenService deviceTokenService,
        TokenHashingService tokenHashingService,
        FamilyJoinTokenVerifier joinTokenVerifier,
        DeviceAuthorizationService deviceAuthorizationService
    ) {
        this.tripRepository = tripRepository;
        this.roleBindingRepository = roleBindingRepository;
        this.deviceRepository = deviceRepository;
        this.bindingActionRepository = bindingActionRepository;
        this.deviceTokenService = deviceTokenService;
        this.tokenHashingService = tokenHashingService;
        this.joinTokenVerifier = joinTokenVerifier;
        this.deviceAuthorizationService = deviceAuthorizationService;
    }

    @Transactional(readOnly = true)
    public FamilyRolesResponse roles(String tripCode, String authorizationHeader) {
        joinTokenVerifier.requireValid(authorizationHeader);
        return buildRoles(requireTrip(tripCode));
    }

    @Transactional
    public DeviceBindingResponse bind(
        String tripCode,
        String authorizationHeader,
        BindDeviceRequest request
    ) {
        joinTokenVerifier.requireValid(authorizationHeader);
        validateBindRequest(request);
        Trip trip = requireTripForUpdate(tripCode);
        FamilyRole role = parseRole(request.role());
        FamilyRoleBinding binding = requireRoleForUpdate(trip, role);

        DeviceBindingResponse replay = replayIfPresent(
            trip, tripCode, request.requestId(), request.deviceId(), request.deviceName(), role, BindingActionType.BIND);
        if (replay != null) {
            return replay;
        }
        if (!binding.isAvailable()) {
            throw conflict(
                "ROLE_ALREADY_BOUND",
                "Family role is already bound; use takeover to replace the device",
                buildRoles(trip));
        }
        rejectExistingDevice(trip, request.deviceId());

        long bindingVersion = binding.getBindingVersion() + 1;
        Instant now = Instant.now();
        String deviceToken = deviceTokenService.issue(
            tripCode, request.deviceId(), bindingVersion, request.requestId());
        TripDevice device = TripDevice.active(
            trip.getId(),
            request.deviceId(),
            role,
            request.deviceName(),
            tokenHashingService.sha256Hex(deviceToken),
            bindingVersion,
            now);

        deviceRepository.save(device);
        binding.activate(request.deviceId(), bindingVersion, now);
        roleBindingRepository.save(binding);
        bindingActionRepository.save(TripDeviceBindingAction.create(
            trip.getId(),
            request.requestId(),
            BindingActionType.BIND,
            role,
            request.deviceId(),
            null,
            bindingVersion,
            now));

        return bindingResponse(
            tripCode, role, request.deviceId(), request.deviceName(), bindingVersion, deviceToken);
    }

    @Transactional
    public DeviceBindingResponse takeover(
        String tripCode,
        String authorizationHeader,
        TakeoverDeviceRequest request
    ) {
        joinTokenVerifier.requireValid(authorizationHeader);
        validateTakeoverRequest(request);
        Trip trip = requireTripForUpdate(tripCode);
        FamilyRole role = parseRole(request.role());
        FamilyRoleBinding binding = requireRoleForUpdate(trip, role);

        if (!request.confirmed()) {
            throw new FamilyBindingException(
                HttpStatus.BAD_REQUEST,
                "TAKEOVER_CONFIRMATION_REQUIRED",
                "Takeover requires confirmed=true");
        }

        DeviceBindingResponse replay = replayIfPresent(
            trip,
            tripCode,
            request.requestId(),
            request.deviceId(),
            request.deviceName(),
            role,
            BindingActionType.TAKEOVER);
        if (replay != null) {
            return replay;
        }
        if (binding.isAvailable()) {
            throw conflict("ROLE_NOT_BOUND", "Family role is not currently bound", buildRoles(trip));
        }
        rejectExistingDevice(trip, request.deviceId());

        Instant now = Instant.now();
        String replacedDeviceId = binding.getActiveDeviceId();
        TripDevice replaced = deviceRepository.findByTripIdAndDeviceIdAndStatus(
                trip.getId(), replacedDeviceId, DeviceStatus.ACTIVE)
            .orElseThrow(() -> conflict(
                "BINDING_STATE_INVALID",
                "Active role binding has no active device record",
                buildRoles(trip)));
        if (replaced.getRole() != role || replaced.getBindingVersion() != binding.getBindingVersion()) {
            throw conflict("BINDING_STATE_INVALID", "Active role binding and device record disagree", buildRoles(trip));
        }

        long bindingVersion = binding.getBindingVersion() + 1;
        String deviceToken = deviceTokenService.issue(
            tripCode, request.deviceId(), bindingVersion, request.requestId());
        TripDevice replacement = TripDevice.active(
            trip.getId(),
            request.deviceId(),
            role,
            request.deviceName(),
            tokenHashingService.sha256Hex(deviceToken),
            bindingVersion,
            now);

        replaced.revoke(now);
        deviceRepository.save(replaced);
        deviceRepository.save(replacement);
        binding.activate(request.deviceId(), bindingVersion, now);
        roleBindingRepository.save(binding);
        bindingActionRepository.save(TripDeviceBindingAction.create(
            trip.getId(),
            request.requestId(),
            BindingActionType.TAKEOVER,
            role,
            request.deviceId(),
            replacedDeviceId,
            bindingVersion,
            now));

        return bindingResponse(
            tripCode, role, request.deviceId(), request.deviceName(), bindingVersion, deviceToken);
    }

    @Transactional
    public DeviceBindingResponse currentBinding(String tripCode, String deviceAuthorizationHeader) {
        AuthenticatedDevice authenticated = deviceAuthorizationService.requireActiveDevice(
            tripCode, deviceAuthorizationHeader);
        TripDevice device = deviceRepository.findByTripIdAndDeviceIdAndStatus(
                authenticated.tripDatabaseId(), authenticated.deviceId(), DeviceStatus.ACTIVE)
            .orElseThrow(this::invalidDeviceToken);
        return bindingResponse(
            tripCode,
            authenticated.role(),
            authenticated.deviceId(),
            device.getDeviceName(),
            authenticated.bindingVersion(),
            null);
    }

    private DeviceBindingResponse replayIfPresent(
        Trip trip,
        String tripCode,
        String requestId,
        String deviceId,
        String deviceName,
        FamilyRole role,
        BindingActionType expectedAction
    ) {
        Optional<TripDeviceBindingAction> existing = bindingActionRepository.findByTripIdAndRequestId(
            trip.getId(), requestId);
        if (existing.isEmpty()) {
            return null;
        }
        TripDeviceBindingAction action = existing.get();
        if (action.getActionType() != expectedAction
            || action.getRole() != role
            || !Objects.equals(action.getNewDeviceId(), deviceId)) {
            throw conflict("IDEMPOTENCY_CONFLICT", "requestId was already used for a different binding action", buildRoles(trip));
        }
        TripDevice device = deviceRepository.findByTripIdAndDeviceId(trip.getId(), deviceId)
            .orElseThrow(() -> conflict(
                "BINDING_STATE_INVALID", "Binding action has no device record", buildRoles(trip)));
        if (!Objects.equals(device.getDeviceName(), deviceName)
            || device.getBindingVersion() != action.getBindingVersion()) {
            throw conflict("IDEMPOTENCY_CONFLICT", "requestId was replayed with different binding data", buildRoles(trip));
        }
        FamilyRoleBinding currentBinding = roleBindingRepository.findByTripIdAndActiveDeviceId(
                trip.getId(), deviceId)
            .orElseThrow(() -> conflict(
                "BINDING_SUPERSEDED", "Historical binding has been superseded", buildRoles(trip)));
        if (device.getStatus() != DeviceStatus.ACTIVE
            || currentBinding.getRole() != role
            || currentBinding.getBindingVersion() != action.getBindingVersion()) {
            throw conflict("BINDING_SUPERSEDED", "Historical binding has been superseded", buildRoles(trip));
        }

        String token = deviceTokenService.issue(tripCode, deviceId, action.getBindingVersion(), requestId);
        return bindingResponse(
            tripCode, role, deviceId, deviceName, action.getBindingVersion(), token);
    }

    private FamilyRolesResponse buildRoles(Trip trip) {
        List<FamilyRoleOptionResponse> roles = roleBindingRepository.findByTripIdOrderByRoleAsc(trip.getId()).stream()
            .sorted(Comparator.comparingInt(binding -> binding.getRole().ordinal()))
            .map(binding -> {
                String deviceName = null;
                if (binding.getActiveDeviceId() != null) {
                    deviceName = deviceRepository.findByTripIdAndDeviceId(trip.getId(), binding.getActiveDeviceId())
                        .map(TripDevice::getDeviceName)
                        .orElse(null);
                }
                return new FamilyRoleOptionResponse(
                    binding.getRole().name(),
                    binding.getDisplayName(),
                    binding.isAvailable() ? "AVAILABLE" : "BOUND",
                    deviceName,
                    binding.getBoundAt(),
                    binding.getBindingVersion());
            })
            .toList();
        return new FamilyRolesResponse(trip.getCode(), roles);
    }

    private void rejectExistingDevice(Trip trip, String deviceId) {
        if (deviceRepository.findByTripIdAndDeviceId(trip.getId(), deviceId).isPresent()) {
            throw conflict("DEVICE_ALREADY_BOUND", "deviceId is already registered for this trip", buildRoles(trip));
        }
    }

    private FamilyRoleBinding requireRoleForUpdate(Trip trip, FamilyRole role) {
        return roleBindingRepository.findForUpdate(trip.getId(), role)
            .orElseThrow(() -> conflict(
                "ROLE_NOT_INITIALIZED", "Family role is not initialized for this trip", buildRoles(trip)));
    }

    private Trip requireTrip(String tripCode) {
        return tripRepository.findByCode(tripCode)
            .orElseThrow(() -> new FamilyBindingException(
                HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "Trip was not found"));
    }

    private Trip requireTripForUpdate(String tripCode) {
        return tripRepository.findByCodeForUpdate(tripCode)
            .orElseThrow(() -> new FamilyBindingException(
                HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "Trip was not found"));
    }

    private FamilyRole parseRole(String rawRole) {
        try {
            return FamilyRole.valueOf(rawRole);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new FamilyBindingException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Family role is invalid");
        }
    }

    private void validateBindRequest(BindDeviceRequest request) {
        if (request == null) {
            throw badRequest("INVALID_BINDING_REQUEST", "Binding request is required");
        }
        validateBindingFields(request.requestId(), request.deviceId(), request.role(), request.deviceName());
    }

    private void validateTakeoverRequest(TakeoverDeviceRequest request) {
        if (request == null) {
            throw badRequest("INVALID_BINDING_REQUEST", "Takeover request is required");
        }
        validateBindingFields(request.requestId(), request.deviceId(), request.role(), request.deviceName());
    }

    private void validateBindingFields(String requestId, String deviceId, String role, String deviceName) {
        requireLength(requestId, 36, "requestId");
        requireLength(deviceId, 64, "deviceId");
        requireLength(role, 32, "role");
        requireLength(deviceName, 128, "deviceName");
    }

    private void requireLength(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw badRequest(
                "INVALID_BINDING_REQUEST",
                field + " is required and must be at most " + maxLength + " characters");
        }
    }

    private DeviceBindingResponse bindingResponse(
        String tripCode,
        FamilyRole role,
        String deviceId,
        String deviceName,
        long bindingVersion,
        String deviceToken
    ) {
        return new DeviceBindingResponse(
            tripCode,
            role.name(),
            role.displayName(),
            deviceId,
            deviceName,
            bindingVersion,
            deviceToken);
    }

    private FamilyBindingException invalidDeviceToken() {
        return new FamilyBindingException(
            HttpStatus.UNAUTHORIZED, "INVALID_DEVICE_TOKEN", "Device Token is invalid or revoked");
    }

    private FamilyBindingException conflict(String code, String message, FamilyRolesResponse latestRoles) {
        return new FamilyBindingException(HttpStatus.CONFLICT, code, message, latestRoles);
    }

    private FamilyBindingException badRequest(String code, String message) {
        return new FamilyBindingException(HttpStatus.BAD_REQUEST, code, message);
    }
}
