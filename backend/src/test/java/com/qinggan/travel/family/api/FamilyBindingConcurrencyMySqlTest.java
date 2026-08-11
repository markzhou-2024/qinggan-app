package com.qinggan.travel.family.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class FamilyBindingConcurrencyMySqlTest {

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
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void twoDevicesRacingForOneAvailableRoleProduceExactlyOneActiveBinding() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(bindAttempt(
                ready,
                start,
                "91919191-9191-9191-9191-919191919191",
                "device-race-a",
                "妈妈的 iPhone A"));
            Future<Integer> second = executor.submit(bindAttempt(
                ready,
                start,
                "92929292-9292-9292-9292-929292929292",
                "device-race-b",
                "妈妈的 iPhone B"));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = List.of(
                first.get(30, TimeUnit.SECONDS),
                second.get(30, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);

            Integer activeMotherDevices = jdbc.queryForObject("""
                select count(*)
                from trip_device d
                join trip t on t.id = d.trip_id
                where t.code = 'qinggan-2026-family'
                  and d.role = 'MOTHER'
                  and d.status = 'ACTIVE'
                """, Integer.class);
            assertThat(activeMotherDevices).isEqualTo(1);

            String activeDeviceId = jdbc.queryForObject("""
                select b.active_device_id
                from trip_family_role_binding b
                join trip t on t.id = b.trip_id
                where t.code = 'qinggan-2026-family'
                  and b.role = 'MOTHER'
                """, String.class);
            assertThat(activeDeviceId).isIn("device-race-a", "device-race-b");
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Integer> bindAttempt(
        CountDownLatch ready,
        CountDownLatch start,
        String requestId,
        String deviceId,
        String deviceName
    ) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start binding race");
            }
            String body = objectMapper.writeValueAsString(
                new BindRequest(requestId, deviceId, "MOTHER", deviceName));
            return mockMvc.perform(post("/api/v1/trips/qinggan-2026-family/family/devices/bind")
                    .header("Authorization", "Bearer test-family-join-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andReturn()
                .getResponse()
                .getStatus();
        };
    }

    private record BindRequest(String requestId, String deviceId, String role, String deviceName) {
    }
}
