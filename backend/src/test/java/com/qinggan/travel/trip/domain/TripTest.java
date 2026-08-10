package com.qinggan.travel.trip.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TripTest {

    @Test
    void rejectsDatesThatDoNotMatchInclusiveDuration() {
        assertThatThrownBy(() -> Trip.create(
                "QINGGAN-2026",
                "青甘大环线10天自驾",
                LocalDate.of(2026, 8, 13),
                LocalDate.of(2026, 8, 22),
                9,
                TripStatus.ACTIVE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("inclusive");
    }

    @Test
    void acceptsTheExactTenDayWindow() {
        Trip trip = Trip.create(
            "QINGGAN-2026",
            "青甘大环线10天自驾",
            LocalDate.of(2026, 8, 13),
            LocalDate.of(2026, 8, 22),
            10,
            TripStatus.ACTIVE);

        assertThat(trip.getDurationDays()).isEqualTo(10);
        assertThat(trip.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 22));
    }
}
