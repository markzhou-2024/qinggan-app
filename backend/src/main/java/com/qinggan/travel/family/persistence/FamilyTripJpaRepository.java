package com.qinggan.travel.family.persistence;

import com.qinggan.travel.trip.domain.Trip;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyTripJpaRepository extends JpaRepository<Trip, Long> {

    Optional<Trip> findByCode(String code);
}
