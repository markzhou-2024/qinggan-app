package com.qinggan.travel.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "place")
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "place_type", nullable = false, length = 32)
    private PlaceType placeType;

    @Column(length = 64)
    private String province;

    @Column(length = 64)
    private String city;

    @Column(name = "latitude_wgs84", precision = 10, scale = 7)
    private BigDecimal latitudeWgs84;

    @Column(name = "longitude_wgs84", precision = 10, scale = 7)
    private BigDecimal longitudeWgs84;

    @Column(name = "latitude_gcj02", precision = 10, scale = 7)
    private BigDecimal latitudeGcj02;

    @Column(name = "longitude_gcj02", precision = 10, scale = 7)
    private BigDecimal longitudeGcj02;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Priority priority;

    @Column(name = "recommended_duration_minutes")
    private Integer recommendedDurationMinutes;

    @Column(columnDefinition = "TEXT")
    private String description;

    protected Place() {
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public PlaceType getPlaceType() { return placeType; }
    public String getProvince() { return province; }
    public String getCity() { return city; }
    public BigDecimal getLatitudeWgs84() { return latitudeWgs84; }
    public BigDecimal getLongitudeWgs84() { return longitudeWgs84; }
    public BigDecimal getLatitudeGcj02() { return latitudeGcj02; }
    public BigDecimal getLongitudeGcj02() { return longitudeGcj02; }
    public Priority getPriority() { return priority; }
    public Integer getRecommendedDurationMinutes() { return recommendedDurationMinutes; }
    public String getDescription() { return description; }
}

