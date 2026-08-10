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
import java.math.BigDecimal;

@Entity
@Table(name = "navigation_point_coordinate")
public class NavigationPointCoordinate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "navigation_point_id", nullable = false)
    private NavigationPoint navigationPoint;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "coordinate_system", nullable = false, length = 16)
    private CoordinateSystem coordinateSystem;

    @Column(name = "primary_coordinate", nullable = false)
    private boolean primaryCoordinate;

    protected NavigationPointCoordinate() {
    }

    public Long getId() { return id; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public CoordinateSystem getCoordinateSystem() { return coordinateSystem; }
    public boolean isPrimaryCoordinate() { return primaryCoordinate; }
}
