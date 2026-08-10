package com.qinggan.travel.itinerary.persistence;

import com.qinggan.travel.trip.domain.TripDay;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripDayJpaRepository extends JpaRepository<TripDay, Long> {
    @EntityGraph(attributePaths = "overnightPlace")
    List<TripDay> findByTripIdOrderBySequenceAsc(Long tripId);
}
