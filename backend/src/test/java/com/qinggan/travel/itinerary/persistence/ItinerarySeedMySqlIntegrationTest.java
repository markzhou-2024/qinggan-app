package com.qinggan.travel.itinerary.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@SpringBootTest
@Testcontainers
@EnabledIfEnvironmentVariable(named = "QINGGAN_RUN_REAL_DB", matches = "true")
class ItinerarySeedMySqlIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("qinggan")
        .withUsername("qinggan")
        .withPassword("qinggan");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.data.redis.repositories.enabled", () -> false);
        registry.add("management.health.redis.enabled", () -> false);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migratesAndSeedsCanonicalItineraryInRealMySql() {
        Integer days = jdbcTemplate.queryForObject("select count(*) from trip_day", Integer.class);
        assertThat(days).isEqualTo(10);

        List<String> dayEight = jdbcTemplate.queryForList("""
            select p.name from trip_stop s
            join trip_day d on d.id = s.trip_day_id
            join place p on p.id = s.place_id
            where d.day_number = 8 order by s.sequence
            """, String.class);
        assertThat(dayEight).containsExactly("祁连县", "门源", "西宁方向", "兰州东部");

        Integer pendingPoints = jdbcTemplate.queryForObject(
            "select count(*) from navigation_point where verification_status = 'PENDING'", Integer.class);
        assertThat(pendingPoints).isGreaterThan(0);
    }
}
