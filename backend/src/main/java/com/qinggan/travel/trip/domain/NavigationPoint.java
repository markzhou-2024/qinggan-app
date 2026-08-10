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
@Table(name = "navigation_point")
public class NavigationPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "navigation_type", nullable = false, length = 32)
    private NavigationType navigationType;

    @Column(name = "latitude_gcj02", precision = 10, scale = 7)
    private BigDecimal latitudeGcj02;

    @Column(name = "longitude_gcj02", precision = 10, scale = 7)
    private BigDecimal longitudeGcj02;

    @Column(name = "navigation_keyword", length = 255)
    private String navigationKeyword;

    @Column(nullable = false)
    private boolean recommended;

    @Column(name = "warning_text", length = 500)
    private String warningText;

    protected NavigationPoint() {
    }

    public Long getId() { return id; }
    public Place getPlace() { return place; }
    public String getName() { return name; }
    public NavigationType getNavigationType() { return navigationType; }
    public BigDecimal getLatitudeGcj02() { return latitudeGcj02; }
    public BigDecimal getLongitudeGcj02() { return longitudeGcj02; }
    public String getNavigationKeyword() { return navigationKeyword; }
    public boolean isRecommended() { return recommended; }
    public String getWarningText() { return warningText; }
}

