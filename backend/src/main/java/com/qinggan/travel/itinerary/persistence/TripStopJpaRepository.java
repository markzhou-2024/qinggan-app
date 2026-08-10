package com.qinggan.travel.itinerary.persistence;

import com.qinggan.travel.trip.domain.TripStop;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripStopJpaRepository extends JpaRepository<TripStop, Long> {
    @EntityGraph(attributePaths = "place")
    List<TripStop> findByTripDayIdInOrderByTripDayIdAscSequenceAsc(Collection<Long> tripDayIds);
}
