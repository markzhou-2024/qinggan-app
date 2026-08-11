package com.qinggan.travel.family.persistence;

import com.qinggan.travel.family.domain.TripDeviceBindingAction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripDeviceBindingActionJpaRepository extends JpaRepository<TripDeviceBindingAction, Long> {

    Optional<TripDeviceBindingAction> findByTripIdAndRequestId(Long tripId, String requestId);
}
