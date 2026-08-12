package com.qinggan.travel.execution.persistence;

import com.qinggan.travel.execution.domain.TripStopExecution;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripStopExecutionJpaRepository extends JpaRepository<TripStopExecution, Long> {
    List<TripStopExecution> findByTripIdOrderByStopIdAsc(Long tripId);
    Optional<TripStopExecution> findByTripIdAndStopId(Long tripId, Long stopId);
}
