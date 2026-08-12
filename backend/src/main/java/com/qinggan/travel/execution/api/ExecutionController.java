package com.qinggan.travel.execution.api;

import com.qinggan.travel.execution.api.dto.ExecutionSnapshotResponse;
import com.qinggan.travel.execution.api.dto.StartExecutionRequest;
import com.qinggan.travel.execution.api.dto.StopExecutionActionRequest;
import com.qinggan.travel.execution.application.ExecutionMutationService;
import com.qinggan.travel.execution.application.ExecutionQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/execution")
public class ExecutionController {

    private final ExecutionQueryService queryService;
    private final ExecutionMutationService mutationService;

    public ExecutionController(ExecutionQueryService queryService, ExecutionMutationService mutationService) {
        this.queryService = queryService;
        this.mutationService = mutationService;
    }

    @GetMapping
    public ResponseEntity<ExecutionSnapshotResponse> snapshot(
        @PathVariable String tripId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        ExecutionSnapshotResponse snapshot = queryService.snapshot(tripId, authorization);
        String etag = etag(snapshot);
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).eTag(etag).body(snapshot);
    }

    @PostMapping("/start")
    public ResponseEntity<ExecutionSnapshotResponse> start(
        @PathVariable String tripId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody StartExecutionRequest request
    ) {
        ExecutionSnapshotResponse snapshot = mutationService.start(tripId, authorization, request);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).eTag(etag(snapshot)).body(snapshot);
    }

    @PostMapping("/stops/{stopId}/actions")
    public ResponseEntity<ExecutionSnapshotResponse> stopAction(
        @PathVariable String tripId,
        @PathVariable String stopId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody StopExecutionActionRequest request
    ) {
        ExecutionSnapshotResponse snapshot = mutationService.applyStopAction(
            tripId, authorization, stopId, request);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).eTag(etag(snapshot)).body(snapshot);
    }

    private String etag(ExecutionSnapshotResponse snapshot) {
        return "\"execution-revision-" + snapshot.revision() + "\"";
    }
}
