package com.qinggan.travel.family.api;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FamilyBindingApiIntegrationTest {

    private static final String TRIP = "qinggan-2026-family";
    private static final String JOIN_TOKEN = "test-family-join-token";
    private static final String JOIN_HEADER = "X-QingGan-Join-Token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listsExactlySixRolesWithoutExposingDeviceTokens() throws Exception {
        mockMvc.perform(get("/api/v1/trips/{tripId}/family/roles", TRIP)
                .header(JOIN_HEADER, JOIN_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tripId").value(TRIP))
            .andExpect(jsonPath("$.roles", hasSize(6)))
            .andExpect(jsonPath("$.roles[*].role", containsInAnyOrder(
                "FATHER", "MOTHER", "OLDER_SISTER", "YOUNGER_BROTHER", "GRANDFATHER", "GRANDMOTHER")))
            .andExpect(jsonPath("$.roles[*].deviceToken").doesNotExist());
    }

    @Test
    void bindRequiresTheFamilyJoinToken() throws Exception {
        mockMvc.perform(post("/api/v1/trips/{tripId}/family/bind", TRIP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bindRequest("11111111-1111-1111-1111-111111111111", "FATHER", "device-a", "爸爸的 iPhone")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_JOIN_TOKEN"));
    }

    @Test
    void bindsAnAvailableRoleAndAuthenticatesTheIssuedDeviceToken() throws Exception {
        String token = bind("22222222-2222-2222-2222-222222222222", "FATHER", "device-a", "爸爸的 iPhone");

        mockMvc.perform(get("/api/v1/trips/{tripId}/family/me", TRIP)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tripId").value(TRIP))
            .andExpect(jsonPath("$.role").value("FATHER"))
            .andExpect(jsonPath("$.displayName").value("爸爸"))
            .andExpect(jsonPath("$.deviceId").value("device-a"))
            .andExpect(jsonPath("$.bindingVersion").value(1));
    }

    @Test
    void ordinaryBindCannotSilentlyReplaceAnOccupiedRole() throws Exception {
        bind("33333333-3333-3333-3333-333333333333", "MOTHER", "device-a", "妈妈旧手机");

        mockMvc.perform(post("/api/v1/trips/{tripId}/family/bind", TRIP)
                .header(JOIN_HEADER, JOIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bindRequest("44444444-4444-4444-4444-444444444444", "MOTHER", "device-b", "妈妈新手机")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ROLE_ALREADY_BOUND"));
    }

    @Test
    void replayingTheSameBindingRequestIsIdempotentAndReturnsTheSameToken() throws Exception {
        String requestId = "55555555-5555-5555-5555-555555555555";
        String first = bind(requestId, "OLDER_SISTER", "device-a", "姐姐的 iPhone");
        String second = bind(requestId, "OLDER_SISTER", "device-a", "姐姐的 iPhone");

        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);

        mockMvc.perform(get("/api/v1/trips/{tripId}/family/me", TRIP)
                .header("Authorization", "Bearer " + second))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bindingVersion").value(1));
    }

    @Test
    void takeoverRevokesTheOldTokenImmediatelyAndActivatesTheReplacementDevice() throws Exception {
        String oldToken = bind("66666666-6666-6666-6666-666666666666", "GRANDFATHER", "device-old", "爷爷旧手机");

        MvcResult takeover = mockMvc.perform(post("/api/v1/trips/{tripId}/family/takeover", TRIP)
                .header(JOIN_HEADER, JOIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bindRequest("77777777-7777-7777-7777-777777777777", "GRANDFATHER", "device-new", "爷爷新手机")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("GRANDFATHER"))
            .andExpect(jsonPath("$.deviceId").value("device-new"))
            .andExpect(jsonPath("$.bindingVersion").value(2))
            .andReturn();

        String newToken = readToken(takeover);

        mockMvc.perform(get("/api/v1/trips/{tripId}/family/me", TRIP)
                .header("Authorization", "Bearer " + oldToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_DEVICE_TOKEN"));

        mockMvc.perform(get("/api/v1/trips/{tripId}/family/me", TRIP)
                .header("Authorization", "Bearer " + newToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("GRANDFATHER"))
            .andExpect(jsonPath("$.deviceId").value("device-new"))
            .andExpect(jsonPath("$.bindingVersion").value(2));
    }

    private String bind(String requestId, String role, String deviceId, String deviceName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/trips/{tripId}/family/bind", TRIP)
                .header(JOIN_HEADER, JOIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bindRequest(requestId, role, deviceId, deviceName)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tripId").value(TRIP))
            .andExpect(jsonPath("$.role").value(role))
            .andExpect(jsonPath("$.deviceId").value(deviceId))
            .andExpect(jsonPath("$.deviceToken").isString())
            .andReturn();
        return readToken(result);
    }

    private String readToken(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return json.get("deviceToken").asText();
    }

    private String bindRequest(String requestId, String role, String deviceId, String deviceName) throws Exception {
        return objectMapper.writeValueAsString(new BindRequest(requestId, role, deviceId, deviceName));
    }

    private record BindRequest(String requestId, String role, String deviceId, String deviceName) {
    }
}
