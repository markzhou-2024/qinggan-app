package com.qinggan.travel.itinerary.persistence;

import com.qinggan.travel.trip.domain.TripStop;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripStopJpaRepository extends JpaRepository<TripStop, Long> {
    @EntityGraph(attributePaths = "place")
    List<TripStop> findByTripDayIdInOrderByTripDayIdAscSequenceAsc(Collection<Long> tripDayIds);

    @EntityGraph(attributePaths = "place")
    @Query("select s from TripStop s where s.id = :id and s.tripDay.trip.id = :tripId")
    Optional<TripStop> findByIdAndTripDayTripId(@Param("id") Long id, @Param("tripId") Long tripId);
}
