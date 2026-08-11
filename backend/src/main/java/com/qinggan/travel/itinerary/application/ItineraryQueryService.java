package com.qinggan.travel.itinerary.application;

import com.qinggan.travel.itinerary.api.dto.DayResponse;
import com.qinggan.travel.itinerary.api.dto.GeoCoordinateResponse;
import com.qinggan.travel.itinerary.api.dto.ItineraryResponse;
import com.qinggan.travel.itinerary.api.dto.NavigationPointResponse;
import com.qinggan.travel.itinerary.api.dto.PlaceResponse;
import com.qinggan.travel.itinerary.api.dto.StayResponse;
import com.qinggan.travel.itinerary.api.dto.StopResponse;
import com.qinggan.travel.itinerary.persistence.NavigationPointJpaRepository;
import com.qinggan.travel.itinerary.persistence.StayJpaRepository;
import com.qinggan.travel.itinerary.persistence.TripDayJpaRepository;
import com.qinggan.travel.itinerary.persistence.TripJpaRepository;
import com.qinggan.travel.itinerary.persistence.TripStopJpaRepository;
import com.qinggan.travel.stay.domain.Stay;
import com.qinggan.travel.trip.domain.NavigationPoint;
import com.qinggan.travel.trip.domain.NavigationPointCoordinate;
import com.qinggan.travel.trip.domain.Place;
import com.qinggan.travel.trip.domain.StopType;
import com.qinggan.travel.trip.domain.Trip;
import com.qinggan.travel.trip.domain.TripDay;
import com.qinggan.travel.trip.domain.TripStop;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItineraryQueryService {

    private final TripJpaRepository tripRepository;
    private final TripDayJpaRepository tripDayRepository;
    private final TripStopJpaRepository tripStopRepository;
    private final NavigationPointJpaRepository navigationPointRepository;
    private final StayJpaRepository stayRepository;

    public ItineraryQueryService(TripJpaRepository tripRepository, TripDayJpaRepository tripDayRepository,
                                 TripStopJpaRepository tripStopRepository,
                                 NavigationPointJpaRepository navigationPointRepository,
                                 StayJpaRepository stayRepository) {
        this.tripRepository = tripRepository;
        this.tripDayRepository = tripDayRepository;
        this.tripStopRepository = tripStopRepository;
        this.navigationPointRepository = navigationPointRepository;
        this.stayRepository = stayRepository;
    }

    @Transactional(readOnly = true)
    public ItineraryResponse itinerary(String tripId) {
        Trip trip = tripRepository.findByCode(tripId).orElseThrow(() -> new TripNotFoundException(tripId));
        List<TripDay> days = tripDayRepository.findByTripIdOrderBySequenceAsc(trip.getId());
        List<Long> dayIds = days.stream().map(TripDay::getId).toList();
        List<TripStop> stops = tripStopRepository.findByTripDayIdInOrderByTripDayIdAscSequenceAsc(dayIds);
        Map<Long, List<TripStop>> stopsByDay = stops.stream()
            .collect(Collectors.groupingBy(stop -> stop.getTripDay().getId()));
        Map<Long, List<NavigationPoint>> pointsByPlace = navigationPointRepository.findByPlaceIdInOrderByIdAsc(
                stops.stream().map(stop -> stop.getPlace().getId()).distinct().toList())
            .stream().collect(Collectors.groupingBy(point -> point.getPlace().getId()));
        Map<Long, Stay> staysByDay = stayRepository.findByTripDayIdIn(dayIds).stream()
            .collect(Collectors.toMap(stay -> stay.getTripDay().getId(), Function.identity()));

        LocalDate effectiveStartDate = trip.getActualStartDate() != null ? trip.getActualStartDate() : trip.getStartDate();
        List<DayResponse> dayResponses = days.stream().map(day -> mapDay(
            day, effectiveStartDate.plusDays(day.getDayNumber() - 1), stopsByDay.getOrDefault(day.getId(), List.of()),
            pointsByPlace, staysByDay.get(day.getId()))).toList();
        LocalDate effectiveEndDate = effectiveStartDate.plusDays(trip.getDurationDays() - 1L);
        return new ItineraryResponse("1.1", trip.getCode(), trip.getName(), effectiveStartDate, effectiveEndDate,
            trip.getStartDate(), trip.getActualStartDate(), trip.getDurationDays(), trip.getStatus().name(),
            trip.getTimeZone(), trip.getRevision(), trip.getUpdatedAt(), dayResponses);
    }

    private DayResponse mapDay(TripDay day, LocalDate resolvedDate, List<TripStop> stops,
                               Map<Long, List<NavigationPoint>> pointsByPlace, Stay stay) {
        List<StopResponse> mappedStops = stops.stream().map(stop -> mapStop(stop,
            pointsByPlace.getOrDefault(stop.getPlace().getId(), List.of()))).toList();
        PlaceResponse origin = stops.stream().filter(stop -> stop.getStopType() == StopType.ORIGIN)
            .findFirst().map(TripStop::getPlace).map(this::mapPlace).orElse(null);
        return new DayResponse(String.valueOf(day.getId()), day.getDayNumber(), resolvedDate, day.getTitle(),
            day.getDayType().name(), day.getPlannedDistanceKm(), day.getPlannedDistanceDisplay(),
            day.getPlannedDriveMinutes(), day.getPlannedDriveDisplay(), origin, mapPlace(day.getOvernightPlace()),
            mappedStops, mapStay(stay));
    }

    private StopResponse mapStop(TripStop stop, List<NavigationPoint> points) {
        List<NavigationPointResponse> responses = points.stream().map(this::mapNavigationPoint).toList();
        NavigationPointResponse recommended = responses.stream().filter(NavigationPointResponse::isRecommended)
            .findFirst().orElse(null);
        List<NavigationPointResponse> alternatives = responses.stream()
            .filter(point -> !point.isRecommended()).toList();
        return new StopResponse(String.valueOf(stop.getId()), stop.getSequence(), stop.getStopType().name(),
            stop.getPriority() == null ? null : stop.getPriority().name(), stop.isOptional(), stop.getStatus().name(),
            mapPlace(stop.getPlace()), recommended, alternatives);
    }

    private NavigationPointResponse mapNavigationPoint(NavigationPoint point) {
        List<GeoCoordinateResponse> coordinates = point.getCoordinates().stream()
            .sorted(Comparator.comparing(NavigationPointCoordinate::isPrimaryCoordinate).reversed())
            .map(coordinate -> new GeoCoordinateResponse(coordinate.getLatitude().doubleValue(),
                coordinate.getLongitude().doubleValue(), coordinate.getCoordinateSystem().name().toLowerCase()))
            .toList();
        GeoCoordinateResponse primary = point.getCoordinates().stream().filter(NavigationPointCoordinate::isPrimaryCoordinate)
            .findFirst().map(coordinate -> new GeoCoordinateResponse(coordinate.getLatitude().doubleValue(),
                coordinate.getLongitude().doubleValue(), coordinate.getCoordinateSystem().name().toLowerCase())).orElse(null);
        List<GeoCoordinateResponse> alternatives = coordinates.stream().filter(coordinate -> !coordinate.equals(primary)).toList();
        return new NavigationPointResponse(String.valueOf(point.getId()), point.getName(), point.getAmapPoiId(), point.getAddress(),
            point.getNavigationType().name(), point.getWarningText(), point.getNavigationKeyword(), point.isRecommended(),
            point.getVerificationStatus().name(), primary, alternatives);
    }

    private PlaceResponse mapPlace(Place place) {
        if (place == null) {
            return null;
        }
        return new PlaceResponse(String.valueOf(place.getId()), place.getName(), place.getPlaceType().name(),
            place.getCity(), place.getPriority() == null ? null : place.getPriority().name());
    }

    private StayResponse mapStay(Stay stay) {
        if (stay == null) {
            return null;
        }
        return new StayResponse(stay.getHotelName(), stay.getAddress(), stay.getPhone(), stay.getCheckInNote(),
            stay.getParkingNote(), stay.getVerificationStatus().name());
    }
}
