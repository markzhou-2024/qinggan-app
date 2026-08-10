package com.qinggan.travel.itinerary.api;

import com.qinggan.travel.itinerary.api.dto.ApiError;
import com.qinggan.travel.itinerary.application.TripNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ItineraryExceptionHandler {

    @ExceptionHandler(TripNotFoundException.class)
    public ResponseEntity<ApiError> tripNotFound(TripNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ApiError("TRIP_NOT_FOUND", exception.getMessage()));
    }
}
