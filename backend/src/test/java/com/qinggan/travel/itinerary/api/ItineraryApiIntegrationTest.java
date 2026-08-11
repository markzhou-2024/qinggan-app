package com.qinggan.travel.itinerary.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ItineraryApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsOneNativeReadyItineraryDocument() throws Exception {
        mockMvc.perform(get("/api/v1/trips/qinggan-2026-family/itinerary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schemaVersion").value("1.1"))
            .andExpect(jsonPath("$.tripId").value("qinggan-2026-family"))
            .andExpect(jsonPath("$.plannedStartDate").value("2026-08-13"))
            .andExpect(jsonPath("$.actualStartDate").doesNotExist())
            .andExpect(jsonPath("$.status").value("PLANNING"))
            .andExpect(jsonPath("$.timeZone").value("Asia/Shanghai"))
            .andExpect(jsonPath("$.revision").value(1))
            .andExpect(jsonPath("$.days.length()").value(10))
            .andExpect(jsonPath("$.days[4].number").value(5))
            .andExpect(jsonPath("$.days[4].stops[0].place.name").value("大柴旦镇"))
            .andExpect(jsonPath("$.days[4].stops[1].place.name").value("U型公路"))
            .andExpect(jsonPath("$.days[4].stops[4].place.name").value("鸣沙山月牙泉"))
            .andExpect(jsonPath("$.days[3].stops[2].recommendedNavigationPoint.name").value("翡翠湖景区停车场"))
            .andExpect(jsonPath("$.days[3].stops[2].recommendedNavigationPoint.verificationStatus").value("PENDING"))
            .andExpect(jsonPath("$.days[3].stops[2].recommendedNavigationPoint.primaryCoordinate").doesNotExist())
            .andExpect(jsonPath("$.days[3].stay.hotelName").value("大柴旦镇住宿（待确认）"))
            .andExpect(jsonPath("$.days[9].destination.name").value("南京"));
    }

    @Test
    void returnsAStableNotFoundErrorForUnknownTrip() throws Exception {
        mockMvc.perform(get("/api/v1/trips/unknown-trip/itinerary"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
    }
}
