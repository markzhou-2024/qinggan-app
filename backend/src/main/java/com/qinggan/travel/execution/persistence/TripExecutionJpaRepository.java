package com.qinggan.travel.execution.persistence;

import com.qinggan.travel.execution.domain.TripExecution;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripExecutionJpaRepository extends JpaRepository<TripExecution, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from TripExecution e where e.tripId = :tripId")
    Optional<TripExecution> findByTripIdForUpdate(@Param("tripId") Long tripId);

    Optional<TripExecution> findByTripId(Long tripId);
}
