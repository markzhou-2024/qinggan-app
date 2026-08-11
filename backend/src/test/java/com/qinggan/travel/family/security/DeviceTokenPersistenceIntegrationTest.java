package com.qinggan.travel.family.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.qinggan.travel.family.api.dto.BindDeviceRequest;
import com.qinggan.travel.family.api.dto.DeviceBindingResponse;
import com.qinggan.travel.family.application.FamilyBindingService;
import com.qinggan.travel.family.domain.TripDevice;
import com.qinggan.travel.family.persistence.FamilyTripJpaRepository;
import com.qinggan.travel.family.persistence.TripDeviceJpaRepository;
import com.qinggan.travel.trip.domain.Trip;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeviceTokenPersistenceIntegrationTest {

    @Autowired
    private FamilyBindingService service;

    @Autowired
    private FamilyTripJpaRepository tripRepository;

    @Autowired
    private TripDeviceJpaRepository deviceRepository;

    @Autowired
    private TokenHashingService tokenHashingService;

    @Test
    void persistsOnlyTheSha256HashOfTheIssuedDeviceToken() {
        DeviceBindingResponse result = service.bind(
            "qinggan-2026-family",
            "Bearer test-family-join-token",
            new BindDeviceRequest(
                "88888888-8888-8888-8888-888888888888",
                "device-security-test",
                "GRANDMOTHER",
                "奶奶的 iPhone"));

        Trip trip = tripRepository.findByCode("qinggan-2026-family").orElseThrow();
        TripDevice persisted = deviceRepository.findByTripIdAndDeviceId(trip.getId(), "device-security-test")
            .orElseThrow();

        assertThat(persisted.getDeviceTokenHash())
            .isEqualTo(tokenHashingService.sha256Hex(result.deviceToken()))
            .hasSize(64)
            .isNotEqualTo(result.deviceToken());
    }
}
