package com.qinggan.travel.stay.domain;

import com.qinggan.travel.trip.domain.TripDay;
import com.qinggan.travel.trip.domain.VerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "stay")
public class Stay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_day_id", nullable = false, unique = true)
    private TripDay tripDay;

    @Column(name = "hotel_name", nullable = false, length = 128)
    private String hotelName;

    @Column(length = 255)
    private String address;

    @Column(length = 64)
    private String phone;

    @Column(name = "check_in_note", length = 500)
    private String checkInNote;

    @Column(name = "parking_note", length = 500)
    private String parkingNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 32)
    private VerificationStatus verificationStatus;

    protected Stay() {
    }

    public Long getId() { return id; }
    public TripDay getTripDay() { return tripDay; }
    public String getHotelName() { return hotelName; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getCheckInNote() { return checkInNote; }
    public String getParkingNote() { return parkingNote; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
}
