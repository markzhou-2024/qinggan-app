package com.qinggan.travel.execution.application;

import com.qinggan.travel.execution.api.dto.ExecutionSnapshotResponse;
import com.qinggan.travel.execution.api.dto.StartExecutionRequest;
import com.qinggan.travel.execution.api.dto.StopExecutionActionRequest;
import com.qinggan.travel.execution.domain.ExecutionActionType;
import com.qinggan.travel.execution.domain.TripExecution;
import com.qinggan.travel.execution.domain.TripExecutionAction;
import com.qinggan.travel.execution.domain.TripStopExecution;
import com.qinggan.travel.execution.persistence.TripExecutionActionJpaRepository;
import com.qinggan.travel.execution.persistence.TripExecutionJpaRepository;
import com.qinggan.travel.execution.persistence.TripStopExecutionJpaRepository;
import com.qinggan.travel.family.application.AuthenticatedDevice;
import com.qinggan.travel.family.application.DeviceAuthorizationService;
import com.qinggan.travel.family.persistence.FamilyTripJpaRepository;
import com.qinggan.travel.itinerary.persistence.TripStopJpaRepository;
import com.qinggan.travel.trip.domain.StopStatus;
import com.qinggan.travel.trip.domain.Trip;
import com.qinggan.travel.trip.domain.TripStop;
import com.qinggan.travel.trip.domain.TripStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionMutationService {

    private final DeviceAuthorizationService authorizationService;
    private final FamilyTripJpaRepository tripRepository;
    private final TripExecutionJpaRepository executionRepository;
    private final TripStopExecutionJpaRepository stopExecutionRepository;
    private final TripExecutionActionJpaRepository actionRepository;
    private final TripStopJpaRepository stopRepository;
    private final ExecutionQueryService queryService;
    private final Clock clock;

    public ExecutionMutationService(DeviceAuthorizationService authorizationService,
                                    FamilyTripJpaRepository tripRepository,
                                    TripExecutionJpaRepository executionRepository,
                                    TripStopExecutionJpaRepository stopExecutionRepository,
                                    TripExecutionActionJpaRepository actionRepository,
                                    TripStopJpaRepository stopRepository,
                                    ExecutionQueryService queryService,
                                    Clock clock) {
        this.authorizationService = authorizationService;
        this.tripRepository = tripRepository;
        this.executionRepository = executionRepository;
        this.stopExecutionRepository = stopExecutionRepository;
        this.actionRepository = actionRepository;
        this.stopRepository = stopRepository;
        this.queryService = queryService;
        this.clock = clock;
    }

    @Transactional
    public ExecutionSnapshotResponse start(String tripCode, String authorizationHeader,
                                           StartExecutionRequest request) {
        AuthenticatedDevice device = authorizationService.requireActiveDevice(tripCode, authorizationHeader);
        validateRequestId(request.requestId());
        TripExecution execution = lockExecution(device.tripDatabaseId());
        Trip trip = lockTrip(tripCode);
        TripExecutionAction existing = actionRepository.findByTripIdAndRequestId(trip.getId(), request.requestId())
            .orElse(null);
        if (existing != null) {
            ensureSameRequest(existing, ExecutionActionType.START, null);
            return queryService.snapshot(trip, execution);
        }
        requireExpectedRevision(request.expectedRevision(), execution, trip);
        if (execution.getStatus() != TripStatus.PLANNING) {
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TRIP_TRANSITION",
                "Trip execution is not in PLANNING", trip, execution);
        }
        Instant now = clock.instant();
        LocalDate actualStartDate = LocalDate.now(clock.withZone(ZoneId.of(trip.getTimeZone())));
        execution.start(actualStartDate, now);
        trip.start(actualStartDate);
        executionRepository.save(execution);
        tripRepository.save(trip);
        actionRepository.save(TripExecutionAction.create(
            trip.getId(), request.requestId(), device.deviceId(), device.role(),
            ExecutionActionType.START, null, now, execution.getRevision()));
        return queryService.snapshot(trip, execution);
    }

    @Transactional
    public ExecutionSnapshotResponse applyStopAction(String tripCode, String authorizationHeader,
                                                      String stopIdValue, StopExecutionActionRequest request) {
        AuthenticatedDevice device = authorizationService.requireActiveDevice(tripCode, authorizationHeader);
        validateRequestId(request.requestId());
        Long stopId = parseStopId(stopIdValue);
        ExecutionActionType actionType = parseAction(request.action());
        TripExecution execution = lockExecution(device.tripDatabaseId());
        Trip trip = lockTrip(tripCode);
        TripExecutionAction existing = actionRepository.findByTripIdAndRequestId(trip.getId(), request.requestId())
            .orElse(null);
        if (existing != null) {
            ensureSameRequest(existing, actionType, stopId);
            return queryService.snapshot(trip, execution);
        }
        requireExpectedRevision(request.expectedRevision(), execution, trip);
        if (execution.getStatus() != TripStatus.STARTED) {
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "TRIP_NOT_STARTED",
                "Stop mutations require a started trip", trip, execution);
        }
        TripStop stop = stopRepository.findByIdAndTripDayTripId(stopId, trip.getId()).orElseThrow(() ->
            failure(HttpStatus.NOT_FOUND, "STOP_NOT_FOUND", "Trip stop was not found", trip, execution));
        StopStatus current = stopExecutionRepository.findByTripIdAndStopId(trip.getId(), stopId)
            .map(TripStopExecution::getStatus)
            .orElse(stop.getStatus());
        StopStatus next = transition(actionType, current, stop.isOptional(), trip, execution);
        Instant now = clock.instant();
        TripStopExecution state = stopExecutionRepository.findByTripIdAndStopId(trip.getId(), stopId).orElse(null);
        if (state == null) {
            state = TripStopExecution.create(trip.getId(), stopId, next, device.role(), device.deviceId(), now);
        } else {
            state.update(next, device.role(), device.deviceId(), now);
        }
        stopExecutionRepository.save(state);
        execution.startRevision(now);
        executionRepository.save(execution);
        actionRepository.save(TripExecutionAction.create(
            trip.getId(), request.requestId(), device.deviceId(), device.role(),
            actionType, stopId, now, execution.getRevision()));
        return queryService.snapshot(trip, execution);
    }

    private TripExecution lockExecution(Long tripId) {
        return executionRepository.findByTripIdForUpdate(tripId).orElseThrow(() ->
            new ExecutionException(HttpStatus.NOT_FOUND, "EXECUTION_NOT_FOUND", "Trip execution was not found"));
    }

    private Trip lockTrip(String tripCode) {
        return tripRepository.findByCodeForUpdate(tripCode).orElseThrow(() ->
            new ExecutionException(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "Trip was not found"));
    }

    private void requireExpectedRevision(long expectedRevision, TripExecution execution, Trip trip) {
        if (expectedRevision != execution.getRevision()) {
            throw failure(HttpStatus.CONFLICT, "EXECUTION_REVISION_CONFLICT",
                "Execution revision is stale", trip, execution);
        }
    }

    private StopStatus transition(ExecutionActionType action, StopStatus current, boolean optional,
                                  Trip trip, TripExecution execution) {
        if (current == StopStatus.COMPLETED) {
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_STOP_TRANSITION",
                "Completed stops are terminal", trip, execution);
        }
        return switch (action) {
            case ARRIVE -> {
                if (current != StopStatus.PLANNED) {
                    throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_STOP_TRANSITION",
                        "ARRIVE requires a planned stop", trip, execution);
                }
                yield StopStatus.ARRIVED;
            }
            case COMPLETE -> {
                if (current != StopStatus.PLANNED && current != StopStatus.ARRIVED
                    && current != StopStatus.SKIPPED) {
                    throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_STOP_TRANSITION",
                        "COMPLETE is not valid for the current stop state", trip, execution);
                }
                yield StopStatus.COMPLETED;
            }
            case SKIP -> {
                if (!optional) {
                    throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "REQUIRED_STOP_CANNOT_BE_SKIPPED",
                        "Required stops cannot be skipped", trip, execution);
                }
                if (current != StopStatus.PLANNED && current != StopStatus.ARRIVED) {
                    throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_STOP_TRANSITION",
                        "SKIP is not valid for the current stop state", trip, execution);
                }
                yield StopStatus.SKIPPED;
            }
            case START -> throw new IllegalStateException("START is not a stop action");
        };
    }

    private ExecutionActionType parseAction(String value) {
        if (value == null) {
            throw new ExecutionException(HttpStatus.BAD_REQUEST, "INVALID_ACTION", "Action is required");
        }
        try {
            ExecutionActionType action = ExecutionActionType.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (action == ExecutionActionType.START) {
                throw new IllegalArgumentException("START is not a stop action");
            }
            return action;
        } catch (IllegalArgumentException exception) {
            throw new ExecutionException(HttpStatus.BAD_REQUEST, "INVALID_ACTION", "Action is not supported");
        }
    }

    private Long parseStopId(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException exception) {
            throw new ExecutionException(HttpStatus.NOT_FOUND, "STOP_NOT_FOUND", "Trip stop was not found");
        }
    }

    private void validateRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new ExecutionException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_ID", "requestId is required");
        }
    }

    private void ensureSameRequest(TripExecutionAction existing, ExecutionActionType actionType, Long stopId) {
        if (existing.getActionType() != actionType || !Objects.equals(existing.getStopId(), stopId)) {
            throw new ExecutionException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                "requestId was already used for different execution inputs");
        }
    }

    private ExecutionException failure(HttpStatus status, String code, String message,
                                       Trip trip, TripExecution execution) {
        return new ExecutionException(status, code, message, queryService.snapshot(trip, execution));
    }
}
