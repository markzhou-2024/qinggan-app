package com.qinggan.travel.execution.domain;

import com.qinggan.travel.trip.domain.TripStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "trip_execution")
public class TripExecution {

    @Id
    @Column(name = "trip_id")
    private Long tripId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TripStatus status;

    @Column(name = "actual_start_date")
    private LocalDate actualStartDate;

    @Column(nullable = false)
    private long revision;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TripExecution() {
    }

    public static TripExecution seeded(Long tripId, TripStatus status, LocalDate actualStartDate, Instant updatedAt) {
        TripExecution execution = new TripExecution();
        execution.tripId = tripId;
        execution.status = status;
        execution.actualStartDate = actualStartDate;
        execution.revision = 0;
        execution.updatedAt = updatedAt;
        return execution;
    }

    public void start(LocalDate startDate, Instant now) {
        if (status != TripStatus.PLANNING) {
            throw new IllegalStateException("Trip execution can only start from PLANNING");
        }
        status = TripStatus.STARTED;
        actualStartDate = startDate;
        revision++;
        updatedAt = now;
    }

    public void startRevision(Instant now) {
        revision++;
        updatedAt = now;
    }

    public Long getTripId() { return tripId; }
    public TripStatus getStatus() { return status; }
    public LocalDate getActualStartDate() { return actualStartDate; }
    public long getRevision() { return revision; }
    public Instant getUpdatedAt() { return updatedAt; }
}
