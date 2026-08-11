package com.qinggan.travel.family.application;

import com.qinggan.travel.family.api.FamilyApiException;
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

    public FamilyBindingService(
        FamilyTripJpaRepository tripRepository,
        FamilyRoleBindingJpaRepository roleBindingRepository,
        TripDeviceJpaRepository deviceRepository,
        TripDeviceBindingActionJpaRepository bindingActionRepository,
        DeviceTokenService deviceTokenService,
        TokenHashingService tokenHashingService,
        FamilyJoinTokenVerifier joinTokenVerifier
    ) {
        this.tripRepository = tripRepository;
        this.roleBindingRepository = roleBindingRepository;
        this.deviceRepository = deviceRepository;
        this.bindingActionRepository = bindingActionRepository;
        this.deviceTokenService = deviceTokenService;
        this.tokenHashingService = tokenHashingService;
        this.joinTokenVerifier = joinTokenVerifier;
    }

    @Transactional(readOnly = true)
    public RolesResult listRoles(String tripCode, String joinToken) {
        joinTokenVerifier.requireValid(joinToken);
        Trip trip = requireTrip(tripCode);
        List<RoleView> roles = roleBindingRepository.findByTripIdOrderByRoleAsc(trip.getId()).stream()
            .sorted(Comparator.comparingInt(binding -> binding.getRole().ordinal()))
            .map(binding -> new RoleView(
                binding.getRole().name(),
                binding.getDisplayName(),
                binding.isAvailable(),
                binding.getBindingVersion()))
            .toList();
        return new RolesResult(tripCode, roles);
    }

    @Transactional
    public BindingResult bind(String tripCode, String joinToken, BindingCommand command) {
        joinTokenVerifier.requireValid(joinToken);
        validateCommand(command);
        Trip trip = requireTrip(tripCode);
        FamilyRole role = parseRole(command.role());

        BindingResult replay = replayIfPresent(trip, tripCode, command, role, BindingActionType.BIND);
        if (replay != null) {
            return replay;
        }

        FamilyRoleBinding binding = roleBindingRepository.findForUpdate(trip.getId(), role)
            .orElseThrow(() -> conflict("ROLE_NOT_INITIALIZED", "Family role is not initialized for this trip"));

        replay = replayIfPresent(trip, tripCode, command, role, BindingActionType.BIND);
        if (replay != null) {
            return replay;
        }
        if (!binding.isAvailable()) {
            throw conflict("ROLE_ALREADY_BOUND", "Family role is already bound; use takeover to replace the device");
        }
        rejectExistingDevice(trip.getId(), command.deviceId());

        long bindingVersion = binding.getBindingVersion() + 1;
        Instant now = Instant.now();
        String deviceToken = deviceTokenService.issue(tripCode, command.deviceId(), bindingVersion, command.requestId());
        String deviceTokenHash = tokenHashingService.sha256Hex(deviceToken);

        TripDevice device = TripDevice.active(
            trip.getId(), command.deviceId(), role, command.deviceName(), deviceTokenHash, bindingVersion, now);
        deviceRepository.save(device);
        binding.activate(command.deviceId(), bindingVersion, now);
        roleBindingRepository.save(binding);
        bindingActionRepository.save(TripDeviceBindingAction.create(
            trip.getId(), command.requestId(), BindingActionType.BIND, role,
            command.deviceId(), null, bindingVersion, now));

        return bindingResult(tripCode, role, command.deviceId(), bindingVersion, deviceToken);
    }

    @Transactional
    public BindingResult takeover(String tripCode, String joinToken, BindingCommand command) {
        joinTokenVerifier.requireValid(joinToken);
        validateCommand(command);
        Trip trip = requireTrip(tripCode);
        FamilyRole role = parseRole(command.role());

        BindingResult replay = replayIfPresent(trip, tripCode, command, role, BindingActionType.TAKEOVER);
        if (replay != null) {
            return replay;
        }

        FamilyRoleBinding binding = roleBindingRepository.findForUpdate(trip.getId(), role)
            .orElseThrow(() -> conflict("ROLE_NOT_INITIALIZED", "Family role is not initialized for this trip"));

        replay = replayIfPresent(trip, tripCode, command, role, BindingActionType.TAKEOVER);
        if (replay != null) {
            return replay;
        }
        rejectExistingDevice(trip.getId(), command.deviceId());

        Instant now = Instant.now();
        String replacedDeviceId = binding.getActiveDeviceId();
        if (replacedDeviceId != null) {
            TripDevice replaced = deviceRepository.findByTripIdAndDeviceIdAndStatus(
                    trip.getId(), replacedDeviceId, DeviceStatus.ACTIVE)
                .orElseThrow(() -> conflict("BINDING_STATE_INVALID", "Active role binding has no active device record"));
            replaced.revoke(now);
            deviceRepository.save(replaced);
        }

        long bindingVersion = binding.getBindingVersion() + 1;
        String deviceToken = deviceTokenService.issue(tripCode, command.deviceId(), bindingVersion, command.requestId());
        String deviceTokenHash = tokenHashingService.sha256Hex(deviceToken);
        TripDevice replacement = TripDevice.active(
            trip.getId(), command.deviceId(), role, command.deviceName(), deviceTokenHash, bindingVersion, now);
        deviceRepository.save(replacement);
        binding.activate(command.deviceId(), bindingVersion, now);
        roleBindingRepository.save(binding);
        bindingActionRepository.save(TripDeviceBindingAction.create(
            trip.getId(), command.requestId(), BindingActionType.TAKEOVER, role,
            command.deviceId(), replacedDeviceId, bindingVersion, now));

        return bindingResult(tripCode, role, command.deviceId(), bindingVersion, deviceToken);
    }

    @Transactional
    public DeviceIdentity authenticate(String tripCode, String authorizationHeader) {
        Trip trip = requireTrip(tripCode);
        String token = bearerToken(authorizationHeader);
        String tokenHash = tokenHashingService.sha256Hex(token);
        TripDevice device = deviceRepository.findByTripIdAndDeviceTokenHashAndStatus(
                trip.getId(), tokenHash, DeviceStatus.ACTIVE)
            .orElseThrow(this::invalidDeviceToken);
        FamilyRoleBinding binding = roleBindingRepository.findByTripIdAndActiveDeviceId(
                trip.getId(), device.getDeviceId())
            .orElseThrow(this::invalidDeviceToken);
        if (binding.getRole() != device.getRole() || binding.getBindingVersion() != device.getBindingVersion()) {
            throw invalidDeviceToken();
        }
        device.touch(Instant.now());
        deviceRepository.save(device);
        return new DeviceIdentity(
            tripCode,
            device.getRole().name(),
            device.getRole().displayName(),
            device.getDeviceId(),
            device.getDeviceName(),
            device.getBindingVersion());
    }

    private BindingResult replayIfPresent(
        Trip trip,
        String tripCode,
        BindingCommand command,
        FamilyRole role,
        BindingActionType expectedAction
    ) {
        Optional<TripDeviceBindingAction> existing = bindingActionRepository.findByTripIdAndRequestId(
            trip.getId(), command.requestId());
        if (existing.isEmpty()) {
            return null;
        }
        TripDeviceBindingAction action = existing.get();
        if (action.getActionType() != expectedAction
            || action.getRole() != role
            || !Objects.equals(action.getNewDeviceId(), command.deviceId())) {
            throw conflict("IDEMPOTENCY_CONFLICT", "requestId was already used for a different binding action");
        }
        TripDevice device = deviceRepository.findByTripIdAndDeviceId(trip.getId(), command.deviceId())
            .orElseThrow(() -> conflict("BINDING_STATE_INVALID", "Binding action has no device record"));
        if (!Objects.equals(device.getDeviceName(), command.deviceName())
            || device.getBindingVersion() != action.getBindingVersion()) {
            throw conflict("IDEMPOTENCY_CONFLICT", "requestId was replayed with different binding data");
        }
        String token = deviceTokenService.issue(
            tripCode, command.deviceId(), action.getBindingVersion(), command.requestId());
        return bindingResult(tripCode, role, command.deviceId(), action.getBindingVersion(), token);
    }

    private void rejectExistingDevice(Long tripId, String deviceId) {
        if (deviceRepository.findByTripIdAndDeviceId(tripId, deviceId).isPresent()) {
            throw conflict("DEVICE_ALREADY_BOUND", "deviceId is already registered for this trip");
        }
    }

    private Trip requireTrip(String tripCode) {
        return tripRepository.findByCode(tripCode)
            .orElseThrow(() -> new FamilyApiException(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "Trip was not found"));
    }

    private FamilyRole parseRole(String rawRole) {
        try {
            return FamilyRole.valueOf(rawRole);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new FamilyApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Family role is invalid");
        }
    }

    private void validateCommand(BindingCommand command) {
        if (command == null) {
            throw badRequest("INVALID_BINDING_REQUEST", "Binding request is required");
        }
        requireLength(command.requestId(), 36, "requestId");
        requireLength(command.deviceId(), 64, "deviceId");
        requireLength(command.deviceName(), 128, "deviceName");
        requireLength(command.role(), 32, "role");
    }

    private void requireLength(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw badRequest("INVALID_BINDING_REQUEST", field + " is required and must be at most " + maxLength + " characters");
        }
    }

    private String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw invalidDeviceToken();
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty() || token.contains(" ")) {
            throw invalidDeviceToken();
        }
        return token;
    }

    private BindingResult bindingResult(
        String tripCode, FamilyRole role, String deviceId, long bindingVersion, String deviceToken
    ) {
        return new BindingResult(
            tripCode, role.name(), role.displayName(), deviceId, bindingVersion, deviceToken);
    }

    private FamilyApiException invalidDeviceToken() {
        return new FamilyApiException(HttpStatus.UNAUTHORIZED, "INVALID_DEVICE_TOKEN", "Device Token is invalid or revoked");
    }

    private FamilyApiException conflict(String code, String message) {
        return new FamilyApiException(HttpStatus.CONFLICT, code, message);
    }

    private FamilyApiException badRequest(String code, String message) {
        return new FamilyApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public record BindingCommand(String requestId, String role, String deviceId, String deviceName) {
    }

    public record RoleView(String role, String displayName, boolean available, long bindingVersion) {
    }

    public record RolesResult(String tripId, List<RoleView> roles) {
    }

    public record BindingResult(
        String tripId,
        String role,
        String displayName,
        String deviceId,
        long bindingVersion,
        String deviceToken
    ) {
    }

    public record DeviceIdentity(
        String tripId,
        String role,
        String displayName,
        String deviceId,
        String deviceName,
        long bindingVersion
    ) {
    }
}
