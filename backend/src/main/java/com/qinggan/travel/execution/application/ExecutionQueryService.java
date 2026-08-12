package com.qinggan.travel.execution.application;

import com.qinggan.travel.execution.api.dto.ExecutionSnapshotResponse;
import com.qinggan.travel.execution.api.dto.ExecutionStopStateResponse;
import com.qinggan.travel.execution.domain.TripExecution;
import com.qinggan.travel.execution.domain.TripStopExecution;
import com.qinggan.travel.execution.persistence.TripExecutionJpaRepository;
import com.qinggan.travel.execution.persistence.TripStopExecutionJpaRepository;
import com.qinggan.travel.family.application.AuthenticatedDevice;
import com.qinggan.travel.family.application.DeviceAuthorizationService;
import com.qinggan.travel.family.persistence.FamilyTripJpaRepository;
import com.qinggan.travel.trip.domain.Trip;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionQueryService {

    private final DeviceAuthorizationService authorizationService;
    private final FamilyTripJpaRepository tripRepository;
    private final TripExecutionJpaRepository executionRepository;
    private final TripStopExecutionJpaRepository stopExecutionRepository;

    public ExecutionQueryService(DeviceAuthorizationService authorizationService,
                                 FamilyTripJpaRepository tripRepository,
                                 TripExecutionJpaRepository executionRepository,
                                 TripStopExecutionJpaRepository stopExecutionRepository) {
        this.authorizationService = authorizationService;
        this.tripRepository = tripRepository;
        this.executionRepository = executionRepository;
        this.stopExecutionRepository = stopExecutionRepository;
    }

    @Transactional(readOnly = true)
    public ExecutionSnapshotResponse snapshot(String tripCode, String authorizationHeader) {
        AuthenticatedDevice device = authorizationService.requireActiveDevice(tripCode, authorizationHeader);
        Trip trip = tripRepository.findById(device.tripDatabaseId())
            .orElseThrow(() -> new ExecutionException(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "Trip was not found"));
        return snapshot(trip, executionRepository.findByTripId(trip.getId()).orElseThrow(() ->
            new ExecutionException(HttpStatus.NOT_FOUND, "EXECUTION_NOT_FOUND", "Trip execution was not found")));
    }

    ExecutionSnapshotResponse snapshot(Trip trip, TripExecution execution) {
        List<ExecutionStopStateResponse> states = stopExecutionRepository
            .findByTripIdOrderByStopIdAsc(trip.getId()).stream()
            .map(this::mapStopState)
            .toList();
        return new ExecutionSnapshotResponse(
            "1.0",
            trip.getCode(),
            execution.getRevision(),
            execution.getStatus(),
            execution.getActualStartDate(),
            states,
            execution.getUpdatedAt());
    }

    private ExecutionStopStateResponse mapStopState(TripStopExecution state) {
        return new ExecutionStopStateResponse(
            String.valueOf(state.getStopId()),
            state.getStatus(),
            state.getUpdatedByRole().name(),
            state.getUpdatedByDeviceId(),
            state.getUpdatedAt());
    }
}
