package com.qinggan.travel.family.persistence;

import com.qinggan.travel.family.domain.DeviceStatus;
import com.qinggan.travel.family.domain.TripDevice;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripDeviceJpaRepository extends JpaRepository<TripDevice, Long> {

    Optional<TripDevice> findByTripIdAndDeviceId(Long tripId, String deviceId);

    Optional<TripDevice> findByTripIdAndDeviceIdAndStatus(Long tripId, String deviceId, DeviceStatus status);

    Optional<TripDevice> findByTripIdAndDeviceTokenHashAndStatus(
        Long tripId, String deviceTokenHash, DeviceStatus status);
}
