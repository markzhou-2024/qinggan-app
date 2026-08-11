package com.qinggan.travel.family.domain;

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
@Table(name = "trip_device_binding_action")
public class TripDeviceBindingAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 16)
    private BindingActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FamilyRole role;

    @Column(name = "new_device_id", nullable = false, length = 64)
    private String newDeviceId;

    @Column(name = "replaced_device_id", length = 64)
    private String replacedDeviceId;

    @Column(name = "binding_version", nullable = false)
    private long bindingVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TripDeviceBindingAction() {
    }

    public static TripDeviceBindingAction create(Long tripId, String requestId, BindingActionType actionType,
                                                 FamilyRole role, String newDeviceId, String replacedDeviceId,
                                                 long bindingVersion, Instant createdAt) {
        TripDeviceBindingAction action = new TripDeviceBindingAction();
        action.tripId = tripId;
        action.requestId = requestId;
        action.actionType = actionType;
        action.role = role;
        action.newDeviceId = newDeviceId;
        action.replacedDeviceId = replacedDeviceId;
        action.bindingVersion = bindingVersion;
        action.createdAt = createdAt;
        return action;
    }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public String getRequestId() { return requestId; }
    public BindingActionType getActionType() { return actionType; }
    public FamilyRole getRole() { return role; }
    public String getNewDeviceId() { return newDeviceId; }
    public String getReplacedDeviceId() { return replacedDeviceId; }
    public long getBindingVersion() { return bindingVersion; }
    public Instant getCreatedAt() { return createdAt; }
}
