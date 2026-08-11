package com.qinggan.travel.family.application;

import com.qinggan.travel.family.domain.DeviceStatus;
import com.qinggan.travel.family.domain.FamilyRoleBinding;
import com.qinggan.travel.family.domain.TripDevice;
import com.qinggan.travel.family.persistence.FamilyRoleBindingJpaRepository;
import com.qinggan.travel.family.persistence.FamilyTripJpaRepository;
import com.qinggan.travel.family.persistence.TripDeviceJpaRepository;
import com.qinggan.travel.family.security.TokenHashingService;
import com.qinggan.travel.trip.domain.Trip;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceAuthorizationService {

    private final FamilyTripJpaRepository tripRepository;
    private final TripDeviceJpaRepository deviceRepository;
    private final FamilyRoleBindingJpaRepository roleBindingRepository;
    private final TokenHashingService tokenHashingService;

    public DeviceAuthorizationService(
        FamilyTripJpaRepository tripRepository,
        TripDeviceJpaRepository deviceRepository,
        FamilyRoleBindingJpaRepository roleBindingRepository,
        TokenHashingService tokenHashingService
    ) {
        this.tripRepository = tripRepository;
        this.deviceRepository = deviceRepository;
        this.roleBindingRepository = roleBindingRepository;
        this.tokenHashingService = tokenHashingService;
    }

    @Transactional
    public AuthenticatedDevice requireActiveDevice(String tripCode, String authorizationHeader) {
        Trip trip = tripRepository.findByCode(tripCode)
            .orElseThrow(() -> new FamilyBindingException(
                HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "Trip was not found"));
        String token = bearerToken(authorizationHeader);
        String tokenHash = tokenHashingService.sha256Hex(token);
        TripDevice device = deviceRepository.findByTripIdAndDeviceTokenHashAndStatus(
                trip.getId(), tokenHash, DeviceStatus.ACTIVE)
            .orElseThrow(this::invalidDeviceToken);
        FamilyRoleBinding binding = roleBindingRepository.findByTripIdAndActiveDeviceId(
                trip.getId(), device.getDeviceId())
            .orElseThrow(this::invalidDeviceToken);
        if (binding.getRole() != device.getRole()
            || binding.getBindingVersion() != device.getBindingVersion()) {
            throw invalidDeviceToken();
        }
        device.touch(Instant.now());
        deviceRepository.save(device);
        return new AuthenticatedDevice(
            trip.getId(), tripCode, device.getDeviceId(), device.getRole(), device.getBindingVersion());
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

    private FamilyBindingException invalidDeviceToken() {
        return new FamilyBindingException(
            HttpStatus.UNAUTHORIZED, "INVALID_DEVICE_TOKEN", "Device Token is invalid or revoked");
    }
}
