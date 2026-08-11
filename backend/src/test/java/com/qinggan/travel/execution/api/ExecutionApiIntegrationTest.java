package com.qinggan.travel.execution.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(ExecutionApiIntegrationTest.FixedClockConfiguration.class)
class ExecutionApiIntegrationTest {

    private static final String TRIP = "qinggan-2026-family";
    private static final String JOIN_TOKEN = "test-family-join-token";
    private static final Instant SERVER_NOW = Instant.parse("2026-08-12T16:30:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void getExecutionUsesDeviceTokenAndSupportsEtag304() throws Exception {
        String token = bind(
            "20000000-0000-0000-0000-000000000001", "FATHER", "execution-device-a", "爸爸的 iPhone");

        mockMvc.perform(get("/api/v1/trips/{tripId}/execution", TRIP)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"execution-revision-0\""))
            .andExpect(jsonPath("$.schemaVersion").value("1.0"))
            .andExpect(jsonPath("$.tripId").value(TRIP))
            .andExpect(jsonPath("$.revision").value(0))
            .andExpect(jsonPath("$.status").value("PLANNING"))
            .andExpect(jsonPath("$.actualStartDate").doesNotExist())
            .andExpect(jsonPath("$.stopStates").isArray());

        mockMvc.perform(get("/api/v1/trips/{tripId}/execution", TRIP)
                .header("Authorization", "Bearer " + token)
                .header("If-None-Match", "\"execution-revision-0\""))
            .andExpect(status().isNotModified())
            .andExpect(header().string("ETag", "\"execution-revision-0\""));
    }

    @Test
    void startTripUsesServerClockInTripTimezoneAndUpdatesItineraryMirror() throws Exception {
        String token = bind(
            "20000000-0000-0000-0000-000000000002", "MOTHER", "execution-device-b", "妈妈的 iPhone");
        long itineraryRevisionBefore = itineraryRevision();

        mockMvc.perform(post("/api/v1/trips/{tripId}/execution/start", TRIP)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "requestId", "30000000-0000-0000-0000-000000000001",
                    "expectedRevision", 0,
                    "occurredAt", "1999-01-01T00:00:00Z"))))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"execution-revision-1\""))
            .andExpect(jsonPath("$.revision").value(1))
            .andExpect(jsonPath("$.status").value("STARTED"))
            .andExpect(jsonPath("$.actualStartDate").value("2026-08-13"));

        mockMvc.perform(get("/api/v1/trips/{tripId}/itinerary", TRIP))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("STARTED"))
            .andExpect(jsonPath("$.actualStartDate").value("2026-08-13"))
            .andExpect(jsonPath("$.revision").value(itineraryRevisionBefore + 1));
    }

    @Test
    void stopMutationUsesRevisionGuardAndAuthenticatedActor() throws Exception {
        String token = bind(
            "20000000-0000-0000-0000-000000000003", "FATHER", "execution-device-c", "爸爸的 iPhone");
        start(token, "30000000-0000-0000-0000-000000000002", 0);
        String stopId = requiredPlannedStopId();

        mockMvc.perform(post("/api/v1/trips/{tripId}/execution/stops/{stopId}/actions", TRIP, stopId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actionRequest("40000000-0000-0000-0000-000000000001", "ARRIVE", 1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revision").value(2))
            .andExpect(jsonPath("$.stopStates[?(@.stopId == '" + stopId + "')].status", hasItem("ARRIVED")))
            .andExpect(jsonPath("$.stopStates[?(@.stopId == '" + stopId + "')].updatedByRole", hasItem("FATHER")))
            .andExpect(jsonPath("$.stopStates[?(@.stopId == '" + stopId + "')].updatedByDeviceId", hasItem("execution-device-c")));

        mockMvc.perform(post("/api/v1/trips/{tripId}/execution/stops/{stopId}/actions", TRIP, stopId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actionRequest("40000000-0000-0000-0000-000000000002", "COMPLETE", 2)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revision").value(3))
            .andExpect(jsonPath("$.stopStates[?(@.stopId == '" + stopId + "')].status", hasItem("COMPLETED")));
    }

    @Test
    void staleRevisionReturns409WithLatestAuthoritativeSnapshot() throws Exception {
        String token = bind(
            "20000000-0000-0000-0000-000000000004", "OLDER_SISTER", "execution-device-d", "姐姐的 iPhone");
        start(token, "30000000-0000-0000-0000-000000000003", 0);
        String stopId = requiredPlannedStopId();

        mockMvc.perform(post("/api/v1/trips/{tripId}/execution/stops/{stopId}/actions", TRIP, stopId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actionRequest("40000000-0000-0000-0000-000000000003", "COMPLETE", 0)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EXECUTION_REVISION_CONFLICT"))
            .andExpect(jsonPath("$.latest.revision").value(1))
            .andExpect(jsonPath("$.latest.status").value("STARTED"));
    }

    @Test
    void optionalSkipAndSkippedToCompletedAreAllowedWhileRequiredSkipIsRejected() throws Exception {
        String token = bind(
            "20000000-0000-0000-0000-000000000005", "YOUNGER_BROTHER", "execution-device-e", "弟弟的 iPhone");
        start(token, "30000000-0000-0000-0000-000000000004", 0);
        String optionalStopId = optionalPlannedStopId();
        String requiredStopId = requiredPlannedStopId();

        mockMvc.perform(post("/api/v1/trips/{tripId}/execution/stops/{stopId}/actions", TRIP, optionalStopId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actionRequest("40000000-0000-0000-0000-000000000004", "SKIP", 1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revision").value(2))
            .andExpect(jsonPath("$.stopStates[?(@.stopId == '" + optionalStopId + "')].status", hasItem("SKIPPED")));

        mockMvc.perform(post("/api/v1/trips/{tripId}/execution/stops/{stopId}/actions", TRIP, optionalStopId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actionRequest("40000000-0000-0000-0000-000000000005", "COMPLETE", 2)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revision").value(3))
            .andExpect(jsonPath("$.stopStates[?(@.stopId == '" + optionalStopId + "')].status", hasItem("COMPLETED")));

        mockMvc.perform(post("/api/v1/trips/{tripId}/execution/stops/{stopId}/actions", TRIP, requiredStopId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actionRequest("40000000-0000-0000-0000-000000000006", "SKIP", 3)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.latest.revision").value(3));
    }

    @Test
    void completedStopCannotRegressAndMutationBeforeStartIsRejected() throws Exception {
        String token = bind(
            "20000000-0000-0000-0000-000000000006", "GRANDFATHER", "execution-device-f", "爷爷的 iPhone");
        String stopId = requiredPlannedStopId();

        mockMvc.perform(post("/api/v1/trips/{tripId}/execution/stops/{stopId}/actions", TRIP, stopId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actionRequest("40000000-0000-0000-0000-000000000007", "COMPLETE", 0)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("TRIP_NOT_STARTED"));

        start(token, "30000000-0000-0000-0000-000000000005", 0);
        mockMvc.perform(post("/api/v1/trips/{tripId}/execution/stops/{stopId}/actions", TRIP, stopId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actionRequest("40000000-0000-0000-0000-000000000008", "COMPLETE", 1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revision").value(2));

        mockMvc.perform(post("/api/v1/trips/{tripId}/execution/stops/{stopId}/actions", TRIP, stopId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actionRequest("40000000-0000-0000-0000-000000000009", "SKIP", 2)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.latest.revision").value(2));
    }

    @Test
    void replayingSameStopRequestNeverIncrementsRevisionTwice() throws Exception {
        String token = bind(
            "20000000-0000-0000-0000-000000000007", "GRANDMOTHER", "execution-device-g", "奶奶的 iPhone");
        start(token, "30000000-0000-0000-0000-000000000006", 0);
        String stopId = requiredPlannedStopId();
        String requestId = "40000000-0000-0000-0000-000000000010";

        mockMvc.perform(post("/api/v1/trips/{tripId}/execution/stops/{stopId}/actions", TRIP, stopId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actionRequest(requestId, "COMPLETE", 1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revision").value(2));

        mockMvc.perform(post("/api/v1/trips/{tripId}/execution/stops/{stopId}/actions", TRIP, stopId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actionRequest(requestId, "COMPLETE", 1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revision").value(2));

        Integer actions = jdbc.queryForObject("""
            select count(*) from trip_execution_action a
            join trip t on t.id = a.trip_id
            where t.code = ? and a.request_id = ?
            """, Integer.class, TRIP, requestId);
        org.assertj.core.api.Assertions.assertThat(actions).isEqualTo(1);
    }

    private String bind(String requestId, String role, String deviceId, String deviceName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/trips/{tripId}/family/devices/bind", TRIP)
                .header("Authorization", "Bearer " + JOIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "requestId", requestId,
                    "deviceId", deviceId,
                    "role", role,
                    "deviceName", deviceName))))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
            .get("deviceToken").asText();
    }

    private JsonNode start(String token, String requestId, long expectedRevision) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/trips/{tripId}/execution/start", TRIP)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "requestId", requestId,
                    "expectedRevision", expectedRevision,
                    "occurredAt", "1999-01-01T00:00:00Z"))))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String actionRequest(String requestId, String action, long expectedRevision) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
            "requestId", requestId,
            "action", action,
            "expectedRevision", expectedRevision,
            "occurredAt", "1999-01-01T00:00:00Z"));
    }

    private long itineraryRevision() {
        Long revision = jdbc.queryForObject(
            "select revision from trip where code = ?", Long.class, TRIP);
        return revision == null ? 0 : revision;
    }

    private String requiredPlannedStopId() {
        Long id = jdbc.queryForObject("""
            select s.id
            from trip_stop s
            join trip_day d on d.id = s.trip_day_id
            join trip t on t.id = d.trip_id
            where t.code = ? and s.optional = false and s.stop_type <> 'ORIGIN' and s.status = 'PLANNED'
            order by d.day_number, s.sequence
            limit 1
            """, Long.class, TRIP);
        return String.valueOf(id);
    }

    private String optionalPlannedStopId() {
        Long id = jdbc.queryForObject("""
            select s.id
            from trip_stop s
            join trip_day d on d.id = s.trip_day_id
            join trip t on t.id = d.trip_id
            where t.code = ? and s.optional = true and s.status = 'PLANNED'
            order by d.day_number, s.sequence
            limit 1
            """, Long.class, TRIP);
        return String.valueOf(id);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock task3TestClock() {
            return Clock.fixed(SERVER_NOW, ZoneOffset.UTC);
        }
    }
}
