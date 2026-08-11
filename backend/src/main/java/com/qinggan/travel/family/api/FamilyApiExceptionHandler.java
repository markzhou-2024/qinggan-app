package com.qinggan.travel.family.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class FamilyApiExceptionHandler {

    @ExceptionHandler(FamilyApiException.class)
    ResponseEntity<FamilyApiError> handleFamilyApiException(FamilyApiException exception) {
        return ResponseEntity.status(exception.getStatus())
            .body(new FamilyApiError(exception.getCode(), exception.getMessage()));
    }

    public record FamilyApiError(String code, String message) {
    }
}
