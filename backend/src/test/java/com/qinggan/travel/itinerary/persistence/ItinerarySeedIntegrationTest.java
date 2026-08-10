package com.qinggan.travel.itinerary.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ItinerarySeedIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedsExactlyTheCanonicalTenDayQingGanItinerary() {
        Integer tripCount = jdbcTemplate.queryForObject(
            "select count(*) from trip where code = 'qinggan-2026-family'", Integer.class);
        assertThat(tripCount).isEqualTo(1);

        List<Object[]> trip = jdbcTemplate.query(
            "select start_date, end_date, duration_days from trip where code = 'qinggan-2026-family'",
            (resultSet, rowNum) -> new Object[] {
                resultSet.getDate("start_date"), resultSet.getDate("end_date"), resultSet.getInt("duration_days")
            });
        assertThat(trip).containsExactly(new Object[] {
            Date.valueOf(LocalDate.of(2026, 8, 13)), Date.valueOf(LocalDate.of(2026, 8, 22)), 10
        });

        List<Integer> dayNumbers = jdbcTemplate.queryForList(
            "select day_number from trip_day order by sequence", Integer.class);
        assertThat(dayNumbers).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        List<String> dayFiveRoute = jdbcTemplate.queryForList("""
            select p.name
            from trip_stop s
            join trip_day d on d.id = s.trip_day_id
            join place p on p.id = s.place_id
            where d.day_number = 5
            order by s.sequence
            """, String.class);
        assertThat(dayFiveRoute).containsExactly("大柴旦镇", "U型公路", "水上雅丹", "敦煌", "鸣沙山月牙泉");

        assertPriorityAndOptionality("莫高窟", "S_PLUS", false);
        assertPriorityAndOptionality("门源", "B", true);
        assertPriorityAndOptionality("嘉峪关", "B", true);

        String finalDestination = jdbcTemplate.queryForObject("""
            select p.name
            from trip_stop s
            join trip_day d on d.id = s.trip_day_id
            join place p on p.id = s.place_id
            where d.day_number = 10 and s.stop_type = 'DESTINATION'
            """, String.class);
        assertThat(finalDestination).isEqualTo("南京");
    }

    @Test
    void marksAnUnverifiedRecommendedNavigationPointPendingWithoutCoordinates() {
        List<Object[]> point = jdbcTemplate.query("""
            select np.verification_status, count(npc.id)
            from navigation_point np
            left join navigation_point_coordinate npc on npc.navigation_point_id = np.id
            where np.name = '翡翠湖景区停车场'
            group by np.verification_status
            """, (resultSet, rowNum) -> new Object[] {
                resultSet.getString("verification_status"), resultSet.getInt(2)
            });

        assertThat(point).containsExactly(new Object[] {"PENDING", 0});
    }

    private void assertPriorityAndOptionality(String placeName, String expectedPriority, boolean expectedOptional) {
        List<Object[]> stop = jdbcTemplate.query("""
            select s.priority, s.optional
            from trip_stop s
            join place p on p.id = s.place_id
            where p.name = ?
            """, (resultSet, rowNum) -> new Object[] {
                resultSet.getString("priority"), resultSet.getBoolean("optional")
            }, placeName);
        assertThat(stop).containsExactly(new Object[] {expectedPriority, expectedOptional});
    }
}
