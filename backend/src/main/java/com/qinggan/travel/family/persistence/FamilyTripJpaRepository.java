package com.qinggan.travel.family.persistence;

import com.qinggan.travel.trip.domain.Trip;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyTripJpaRepository extends JpaRepository<Trip, Long> {

    Optional<Trip> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.code = :code")
    Optional<Trip> findByCodeForUpdate(@Param("code") String code);
}
