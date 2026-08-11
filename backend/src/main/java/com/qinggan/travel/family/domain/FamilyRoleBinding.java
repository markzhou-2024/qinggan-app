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
@Table(name = "trip_family_role_binding")
public class FamilyRoleBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FamilyRole role;

    @Column(name = "display_name", nullable = false, length = 32)
    private String displayName;

    @Column(name = "active_device_id", length = 64)
    private String activeDeviceId;

    @Column(name = "binding_version", nullable = false)
    private long bindingVersion;

    @Column(name = "bound_at")
    private Instant boundAt;

    protected FamilyRoleBinding() {
    }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public FamilyRole getRole() { return role; }
    public String getDisplayName() { return displayName; }
    public String getActiveDeviceId() { return activeDeviceId; }
    public long getBindingVersion() { return bindingVersion; }
    public Instant getBoundAt() { return boundAt; }

    public boolean isAvailable() {
        return activeDeviceId == null;
    }

    public void activate(String deviceId, long newBindingVersion, Instant at) {
        this.activeDeviceId = deviceId;
        this.bindingVersion = newBindingVersion;
        this.boundAt = at;
    }
}
