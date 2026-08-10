package com.qinggan.travel.itinerary.persistence;

import com.qinggan.travel.trip.domain.NavigationPoint;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NavigationPointJpaRepository extends JpaRepository<NavigationPoint, Long> {
    @EntityGraph(attributePaths = "coordinates")
    List<NavigationPoint> findByPlaceIdInOrderByIdAsc(Collection<Long> placeIds);
}
