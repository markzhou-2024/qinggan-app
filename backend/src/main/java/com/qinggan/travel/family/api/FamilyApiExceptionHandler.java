package com.qinggan.travel.family.api;

import com.qinggan.travel.family.api.dto.FamilyConflictResponse;
import com.qinggan.travel.family.application.FamilyBindingException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class FamilyApiExceptionHandler {

    @ExceptionHandler(FamilyBindingException.class)
    ResponseEntity<FamilyConflictResponse> handleFamilyBindingException(FamilyBindingException exception) {
        return ResponseEntity.status(exception.getStatus())
            .body(new FamilyConflictResponse(
                exception.getCode(), exception.getMessage(), exception.getLatestRoles()));
    }
}
