package com.qinggan.travel.execution.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ExecutionRevisionConcurrencyMySqlTest {

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
    void sameRevisionStopMutationsHaveExactlyOneMySqlWinner() throws Exception {
        String fatherToken = bind(
            "91000000-0000-0000-0000-000000000001", "FATHER", "mysql-execution-father");
        String motherToken = bind(
            "91000000-0000-0000-0000-000000000002", "MOTHER", "mysql-execution-mother");
        start(fatherToken, "92000000-0000-0000-0000-000000000001");
        String stopId = requiredPlannedStopId();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(stopAttempt(
                ready, start, fatherToken, stopId,
                "93000000-0000-0000-0000-000000000001"));
            Future<Integer> second = executor.submit(stopAttempt(
                ready, start, motherToken, stopId,
                "93000000-0000-0000-0000-000000000002"));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = List.of(
                first.get(30, TimeUnit.SECONDS),
                second.get(30, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);

            Long revision = jdbc.queryForObject("""
                select e.revision from trip_execution e
                join trip t on t.id = e.trip_id
                where t.code = 'qinggan-2026-family'
                """, Long.class);
            assertThat(revision).isEqualTo(2L);

            Integer actionCount = jdbc.queryForObject("""
                select count(*) from trip_execution_action a
                join trip t on t.id = a.trip_id
                where t.code = 'qinggan-2026-family'
                  and a.action_type = 'ARRIVE'
                """, Integer.class);
            assertThat(actionCount).isEqualTo(1);

            String status = jdbc.queryForObject("""
                select s.status from trip_stop_execution s
                join trip t on t.id = s.trip_id
                where t.code = 'qinggan-2026-family' and s.stop_id = ?
                """, String.class, Long.valueOf(stopId));
            assertThat(status).isEqualTo("ARRIVED");
        } finally {
            executor.shutdownNow();
        }
    }

    private String bind(String requestId, String role, String deviceId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/trips/qinggan-2026-family/family/devices/bind")
                .header("Authorization", "Bearer test-family-join-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BindRequest(requestId, deviceId, role, deviceId))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("deviceToken").asText();
    }

    private void start(String token, String requestId) throws Exception {
        mockMvc.perform(post("/api/v1/trips/qinggan-2026-family/execution/start")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StartRequest(requestId, 0))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    private Callable<Integer> stopAttempt(CountDownLatch ready, CountDownLatch start,
                                          String token, String stopId, String requestId) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start execution race");
            }
            return mockMvc.perform(post(
                    "/api/v1/trips/qinggan-2026-family/execution/stops/{stopId}/actions", stopId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new StopRequest(requestId, 1, "ARRIVE"))))
                .andReturn()
                .getResponse()
                .getStatus();
        };
    }

    private String requiredPlannedStopId() {
        Long id = jdbc.queryForObject("""
            select s.id from trip_stop s
            join trip_day d on d.id = s.trip_day_id
            join trip t on t.id = d.trip_id
            where t.code = 'qinggan-2026-family'
              and s.optional = false and s.stop_type <> 'ORIGIN' and s.status = 'PLANNED'
            order by d.day_number, s.sequence limit 1
            """, Long.class);
        return String.valueOf(id);
    }

    private record BindRequest(String requestId, String deviceId, String role, String deviceName) {}
    private record StartRequest(String requestId, long expectedRevision) {}
    private record StopRequest(String requestId, long expectedRevision, String action) {}
}
