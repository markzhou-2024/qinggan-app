package com.qinggan.travel.execution.domain;

import com.qinggan.travel.family.domain.FamilyRole;
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
@Table(name = "trip_execution_action")
public class TripExecutionAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FamilyRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 16)
    private ExecutionActionType actionType;

    @Column(name = "stop_id")
    private Long stopId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "applied_revision", nullable = false)
    private long appliedRevision;

    protected TripExecutionAction() {
    }

    public static TripExecutionAction create(Long tripId, String requestId, String deviceId,
                                             FamilyRole role, ExecutionActionType actionType,
                                             Long stopId, Instant createdAt, long appliedRevision) {
        TripExecutionAction action = new TripExecutionAction();
        action.tripId = tripId;
        action.requestId = requestId;
        action.deviceId = deviceId;
        action.role = role;
        action.actionType = actionType;
        action.stopId = stopId;
        action.createdAt = createdAt;
        action.appliedRevision = appliedRevision;
        return action;
    }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public String getRequestId() { return requestId; }
    public String getDeviceId() { return deviceId; }
    public FamilyRole getRole() { return role; }
    public ExecutionActionType getActionType() { return actionType; }
    public Long getStopId() { return stopId; }
    public Instant getCreatedAt() { return createdAt; }
    public long getAppliedRevision() { return appliedRevision; }
}
