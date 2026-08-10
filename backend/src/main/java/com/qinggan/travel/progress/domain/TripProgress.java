package com.qinggan.travel.progress.domain;

import com.qinggan.travel.trip.domain.Trip;
import com.qinggan.travel.trip.domain.TripStop;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "trip_progress")
public class TripProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false, unique = true)
    private Trip trip;

    @Column(name = "current_day_number", nullable = false)
    private int currentDayNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_stop_id")
    private TripStop currentStop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_completed_stop_id")
    private TripStop lastCompletedStop;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProgressState state;

    @Column(nullable = false)
    private long revision;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TripProgress() {
    }

    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public int getCurrentDayNumber() { return currentDayNumber; }
    public TripStop getCurrentStop() { return currentStop; }
    public TripStop getLastCompletedStop() { return lastCompletedStop; }
    public ProgressState getState() { return state; }
    public long getRevision() { return revision; }
    public Instant getUpdatedAt() { return updatedAt; }
}
