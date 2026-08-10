package com.qinggan.travel.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;

@Entity
@Table(name = "trip_stop")
public class TripStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_day_id", nullable = false)
    private TripDay tripDay;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_type", nullable = false, length = 32)
    private StopType stopType;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Priority priority;

    @Column(name = "planned_arrival_time")
    private LocalTime plannedArrivalTime;

    @Column(name = "planned_departure_time")
    private LocalTime plannedDepartureTime;

    @Column(name = "planned_duration_minutes")
    private Integer plannedDurationMinutes;

    @Column(nullable = false)
    private boolean optional;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StopStatus status;

    protected TripStop() {
    }

    public Long getId() { return id; }
    public TripDay getTripDay() { return tripDay; }
    public Place getPlace() { return place; }
    public int getSequence() { return sequence; }
    public StopType getStopType() { return stopType; }
    public Priority getPriority() { return priority; }
    public LocalTime getPlannedArrivalTime() { return plannedArrivalTime; }
    public LocalTime getPlannedDepartureTime() { return plannedDepartureTime; }
    public Integer getPlannedDurationMinutes() { return plannedDurationMinutes; }
    public boolean isOptional() { return optional; }
    public StopStatus getStatus() { return status; }
}
