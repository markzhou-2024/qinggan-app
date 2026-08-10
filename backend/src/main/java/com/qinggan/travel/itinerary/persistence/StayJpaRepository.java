package com.qinggan.travel.itinerary.persistence;

import com.qinggan.travel.stay.domain.Stay;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StayJpaRepository extends JpaRepository<Stay, Long> {
    List<Stay> findByTripDayIdIn(Collection<Long> tripDayIds);
}
