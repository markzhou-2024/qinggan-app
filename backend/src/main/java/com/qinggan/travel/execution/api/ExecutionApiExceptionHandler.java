package com.qinggan.travel.execution.api;

import com.qinggan.travel.execution.api.dto.ExecutionErrorResponse;
import com.qinggan.travel.execution.application.ExecutionException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExecutionApiExceptionHandler {

    @ExceptionHandler(ExecutionException.class)
    ResponseEntity<ExecutionErrorResponse> handleExecutionException(ExecutionException exception) {
        return ResponseEntity.status(exception.getStatus())
            .body(new ExecutionErrorResponse(exception.getCode(), exception.getMessage(), exception.getLatest()));
    }
}
