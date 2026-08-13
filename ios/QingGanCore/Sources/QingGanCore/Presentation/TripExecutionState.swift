import Foundation

public struct TripDayProgress: Equatable, Sendable {
    public let completed: Int
    public let resolved: Int
    public let total: Int

    public init(completed: Int, resolved: Int, total: Int) {
        self.completed = completed
        self.resolved = resolved
        self.total = total
    }
}

/// A pure, read-only projection of the itinerary, latest server execution state, and local intents.
/// Server state is always the authority; pending actions only project legal local transitions.
public struct TripExecutionState: Equatable, Sendable {
    private let trip: Trip
    private let snapshot: TripExecutionSnapshot?
    private let pendingActions: [PendingExecutionAction]

    public init(trip: Trip, snapshot: TripExecutionSnapshot?, pendingActions: [PendingExecutionAction]) {
        self.trip = trip
        self.snapshot = snapshot
        self.pendingActions = pendingActions
    }

    public func status(for stop: TripStop) -> StopStatus {
        let serverStatus = serverStatus(for: stop) ?? stop.status
        return pendingActions
            .filter { $0.tripID == trip.id && $0.stopID == stop.id }
            .sorted(by: pendingOrder)
            .reduce(serverStatus) { projectedStatus, action in
                legalTransition(from: projectedStatus, for: stop, action: action.action) ?? projectedStatus
            }
    }

    public func nextStop(on day: TripDay) -> TripStop? {
        day.stops
            .sorted { $0.sequence < $1.sequence }
            .first { stop in
                guard stop.type != .origin, status(for: stop) != .moved else { return false }
                switch status(for: stop) {
                case .completed, .skipped:
                    return false
                case .planned, .arrived:
                    return true
                case .moved:
                    return false
                }
            }
    }

    public func progress(for day: TripDay) -> TripDayProgress {
        let actionableStops = day.stops.filter { $0.type != .origin }
        let statuses = actionableStops.map(status(for:))
        return TripDayProgress(
            completed: statuses.filter { $0 == .completed }.count,
            resolved: statuses.filter { $0 == .completed || $0 == .skipped }.count,
            total: actionableStops.count
        )
    }

    /// Reconciles an intent against confirmed plan/server state only, never against other local intents.
    public func resolution(of action: PendingExecutionAction) -> PendingActionResolution {
        guard action.tripID == trip.id else { return .invalid }

        if action.action == .start {
            guard action.stopID == nil else { return .invalid }
            switch confirmedLifecycleStatus {
            case .planning: return .retryable
            case .started, .completed: return .resolvedNoOp
            }
        }

        guard let stopID = action.stopID,
              let stop = trip.days.lazy.flatMap(\.stops).first(where: { $0.id == stopID }) else {
            return .invalid
        }

        let confirmed = serverStatus(for: stop) ?? stop.status
        switch confirmed {
        case .completed:
            return .resolvedNoOp
        case .moved:
            return .invalid
        case .skipped where action.action == .skip:
            return .resolvedNoOp
        case .arrived where action.action == .arrive:
            return .resolvedNoOp
        default:
            return legalTransition(from: confirmed, for: stop, action: action.action) == nil
                ? .invalid
                : .retryable
        }
    }

    private var confirmedLifecycleStatus: TripLifecycleStatus {
        snapshot?.status ?? trip.status
    }

    private func serverStatus(for stop: TripStop) -> StopStatus? {
        guard snapshot?.tripID == trip.id else { return nil }
        return snapshot?.stopStates.last(where: { $0.stopID == stop.id })?.status
    }

    private func pendingOrder(_ lhs: PendingExecutionAction, _ rhs: PendingExecutionAction) -> Bool {
        if lhs.createdAt != rhs.createdAt { return lhs.createdAt < rhs.createdAt }
        return lhs.id.uuidString < rhs.id.uuidString
    }

    private func legalTransition(
        from status: StopStatus,
        for stop: TripStop,
        action: ExecutionAction
    ) -> StopStatus? {
        switch (status, action) {
        case (.planned, .arrive):
            return .arrived
        case (.planned, .complete):
            return .completed
        case (.planned, .skip) where stop.isOptional:
            return .skipped
        case (.arrived, .complete):
            return .completed
        case (.arrived, .skip) where stop.isOptional:
            return .skipped
        case (.skipped, .complete) where stop.isOptional:
            return .completed
        default:
            return nil
        }
    }
}
