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
import java.time.LocalDate;

@Entity
@Table(name = "trip_day")
public class TripDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column
    private LocalDate date;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, length = 32)
    private DayType dayType;

    @Column(name = "planned_distance_km")
    private Integer plannedDistanceKm;

    @Column(name = "planned_distance_display", length = 64)
    private String plannedDistanceDisplay;

    @Column(name = "planned_drive_minutes")
    private Integer plannedDriveMinutes;

    @Column(name = "planned_drive_display", length = 64)
    private String plannedDriveDisplay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "overnight_place_id")
    private Place overnightPlace;

    @Column(nullable = false)
    private int sequence;

    protected TripDay() {
    }

    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public int getDayNumber() { return dayNumber; }
    public LocalDate getDate() { return date; }
    public String getTitle() { return title; }
    public DayType getDayType() { return dayType; }
    public Integer getPlannedDistanceKm() { return plannedDistanceKm; }
    public String getPlannedDistanceDisplay() { return plannedDistanceDisplay; }
    public Integer getPlannedDriveMinutes() { return plannedDriveMinutes; }
    public String getPlannedDriveDisplay() { return plannedDriveDisplay; }
    public Place getOvernightPlace() { return overnightPlace; }
    public int getSequence() { return sequence; }
}
