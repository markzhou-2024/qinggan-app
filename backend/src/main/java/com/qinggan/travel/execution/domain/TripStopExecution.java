package com.qinggan.travel.execution.domain;

import com.qinggan.travel.family.domain.FamilyRole;
import com.qinggan.travel.trip.domain.StopStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "trip_stop_execution")
public class TripStopExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "stop_id", nullable = false)
    private Long stopId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StopStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "updated_by_role", nullable = false, length = 32)
    private FamilyRole updatedByRole;

    @Column(name = "updated_by_device_id", nullable = false, length = 64)
    private String updatedByDeviceId;

    protected TripStopExecution() {
    }

    public static TripStopExecution create(Long tripId, Long stopId, StopStatus status,
                                            FamilyRole role, String deviceId, Instant updatedAt) {
        TripStopExecution execution = new TripStopExecution();
        execution.tripId = tripId;
        execution.stopId = stopId;
        execution.status = status;
        execution.updatedByRole = role;
        execution.updatedByDeviceId = deviceId;
        execution.updatedAt = updatedAt;
        return execution;
    }

    public void update(StopStatus nextStatus, FamilyRole role, String deviceId, Instant now) {
        status = nextStatus;
        updatedByRole = role;
        updatedByDeviceId = deviceId;
        updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public Long getStopId() { return stopId; }
    public StopStatus getStatus() { return status; }
    public Instant getUpdatedAt() { return updatedAt; }
    public FamilyRole getUpdatedByRole() { return updatedByRole; }
    public String getUpdatedByDeviceId() { return updatedByDeviceId; }
}
