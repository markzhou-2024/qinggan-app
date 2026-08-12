package com.qinggan.travel.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Entity
@Table(name = "trip")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "actual_start_date")
    private LocalDate actualStartDate;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TripStatus status;

    @Column(nullable = false)
    private long revision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Trip() {
    }

    private Trip(String code, String name, LocalDate startDate, LocalDate endDate,
                 int durationDays, TripStatus status) {
        this.code = Objects.requireNonNull(code);
        this.name = Objects.requireNonNull(name);
        this.startDate = Objects.requireNonNull(startDate);
        this.endDate = Objects.requireNonNull(endDate);
        this.durationDays = durationDays;
        this.status = Objects.requireNonNull(status);
        this.timeZone = "Asia/Shanghai";
        this.revision = 1;
        validateInclusiveDuration();
    }

    public static Trip create(String code, String name, LocalDate startDate, LocalDate endDate,
                              int durationDays, TripStatus status) {
        return new Trip(code, name, startDate, endDate, durationDays, status);
    }

    private void validateInclusiveDuration() {
        long inclusiveDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (inclusiveDays != durationDays || durationDays <= 0) {
            throw new IllegalArgumentException("Trip inclusive date duration must match durationDays");
        }
    }

    @PrePersist
    void onCreate() {
        validateInclusiveDuration();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        validateInclusiveDuration();
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public LocalDate getActualStartDate() { return actualStartDate; }
    public String getTimeZone() { return timeZone; }
    public int getDurationDays() { return durationDays; }
    public TripStatus getStatus() { return status; }
    public long getRevision() { return revision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void start(LocalDate actualStartDate) {
        if (status != TripStatus.PLANNING) {
            throw new IllegalStateException("Trip can only start from PLANNING");
        }
        this.status = TripStatus.STARTED;
        this.actualStartDate = Objects.requireNonNull(actualStartDate);
        this.revision++;
    }
}
