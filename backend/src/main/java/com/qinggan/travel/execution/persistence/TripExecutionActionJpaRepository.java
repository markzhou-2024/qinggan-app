package com.qinggan.travel.execution.persistence;

import com.qinggan.travel.execution.domain.TripExecutionAction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripExecutionActionJpaRepository extends JpaRepository<TripExecutionAction, Long> {
    Optional<TripExecutionAction> findByTripIdAndRequestId(Long tripId, String requestId);
}
