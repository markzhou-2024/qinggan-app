package com.qinggan.travel.itinerary.api;

import com.qinggan.travel.itinerary.api.dto.ItineraryResponse;
import com.qinggan.travel.itinerary.application.ItineraryQueryService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips")
public class ItineraryController {

    private final ItineraryQueryService queryService;

    public ItineraryController(ItineraryQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{tripId}/itinerary")
    public ResponseEntity<ItineraryResponse> itinerary(@PathVariable String tripId) {
        ItineraryResponse response = queryService.itinerary(tripId);
        String etag = "\"trip-" + response.tripId() + "-" + response.revision() + "\"";
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).eTag(etag).body(response);
    }
}
