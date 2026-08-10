package com.qinggan.travel.trip.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.List;

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

    @Column(length = 255)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "navigation_type", nullable = false, length = 32)
    private NavigationType navigationType;

    @Column(name = "navigation_keyword", length = 255)
    private String navigationKeyword;

    @Column(nullable = false)
    private boolean recommended;

    @Column(name = "warning_text", length = 500)
    private String warningText;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 32)
    private VerificationStatus verificationStatus;

    @OneToMany(mappedBy = "navigationPoint", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<NavigationPointCoordinate> coordinates;

    protected NavigationPoint() {
    }

    public Long getId() { return id; }
    public Place getPlace() { return place; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public NavigationType getNavigationType() { return navigationType; }
    public String getNavigationKeyword() { return navigationKeyword; }
    public boolean isRecommended() { return recommended; }
    public String getWarningText() { return warningText; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public List<NavigationPointCoordinate> getCoordinates() { return coordinates; }
}
