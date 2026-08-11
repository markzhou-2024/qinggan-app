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
@Table(name = "trip_device")
public class TripDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FamilyRole role;

    @Column(name = "device_name", nullable = false, length = 128)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeviceStatus status;

    @Column(name = "device_token_hash", nullable = false, length = 64)
    private String deviceTokenHash;

    @Column(name = "binding_version", nullable = false)
    private long bindingVersion;

    @Column(name = "bound_at", nullable = false)
    private Instant boundAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    protected TripDevice() {
    }

    public static TripDevice active(Long tripId, String deviceId, FamilyRole role, String deviceName,
                                    String deviceTokenHash, long bindingVersion, Instant boundAt) {
        TripDevice device = new TripDevice();
        device.tripId = tripId;
        device.deviceId = deviceId;
        device.role = role;
        device.deviceName = deviceName;
        device.status = DeviceStatus.ACTIVE;
        device.deviceTokenHash = deviceTokenHash;
        device.bindingVersion = bindingVersion;
        device.boundAt = boundAt;
        return device;
    }

    public void revoke(Instant at) {
        status = DeviceStatus.REVOKED;
        revokedAt = at;
    }

    public void touch(Instant at) {
        lastSeenAt = at;
    }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public String getDeviceId() { return deviceId; }
    public FamilyRole getRole() { return role; }
    public String getDeviceName() { return deviceName; }
    public DeviceStatus getStatus() { return status; }
    public String getDeviceTokenHash() { return deviceTokenHash; }
    public long getBindingVersion() { return bindingVersion; }
    public Instant getBoundAt() { return boundAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}
