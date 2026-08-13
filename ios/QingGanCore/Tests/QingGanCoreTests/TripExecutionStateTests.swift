import Foundation
import XCTest
@testable import QingGanCore

final class TripExecutionStateTests: XCTestCase {
    func testExecutionSnapshotDecodesTask3ServerDatesAndActors() throws {
        let json = """
        {"schemaVersion":"1.0","tripId":"qinggan-2026-family","revision":22,"status":"STARTED","actualStartDate":"2026-08-13","updatedAt":"2026-08-15T03:26:04Z","stopStates":[{"stopId":"12","status":"COMPLETED","updatedAt":"2026-08-15T03:26:04Z","updatedByRole":"FATHER","updatedByDeviceId":"device-dad-a"}]}
        """
        let snapshot = try JSONDecoder().decode(TripExecutionSnapshot.self, from: Data(json.utf8))
        XCTAssertEqual(snapshot.tripID, "qinggan-2026-family")
        XCTAssertEqual(snapshot.revision, 22)
        XCTAssertEqual(snapshot.status, .started)
        XCTAssertEqual(snapshot.stopStates[0].status, .completed)
        XCTAssertEqual(snapshot.stopStates[0].updatedByRole, .father)
        XCTAssertEqual(snapshot.stopStates[0].updatedByDeviceID, "device-dad-a")
        XCTAssertEqual(TripDateCodec.day.string(from: try XCTUnwrap(snapshot.actualStartDate)), "2026-08-13")
    }

    func testPlanningSnapshotAllowsNoActualStartDate() throws {
        let json = """
        {"schemaVersion":"1.0","tripId":"qinggan-2026-family","revision":0,"status":"PLANNING","actualStartDate":null,"updatedAt":"2026-08-15T03:26:04Z","stopStates":[]}
        """

        let snapshot = try JSONDecoder().decode(TripExecutionSnapshot.self, from: Data(json.utf8))

        XCTAssertEqual(snapshot.status, .planning)
        XCTAssertNil(snapshot.actualStartDate)
    }

    func testPendingActionRoundTripsWithoutSecretFields() throws {
        let action = PendingExecutionAction(id: UUID(uuidString: "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA")!, tripID: "qinggan-2026-family", stopID: "12", action: .complete, occurredAt: date("2026-08-15T03:20:00Z"), createdAt: date("2026-08-15T03:21:00Z"), deviceID: "device-dad-a", bindingVersion: 2)
        let data = try JSONEncoder().encode(action)
        let text = String(decoding: data, as: UTF8.self)
        XCTAssertFalse(text.localizedCaseInsensitiveContains("token"))
        XCTAssertEqual(try JSONDecoder().decode(PendingExecutionAction.self, from: data), action)
    }

    func testServerOverrideAndPendingProjectionDriveNextStopAndProgress() throws {
        let trip = try fixtureTrip()
        let day = trip.days[0]
        let snapshot = TripExecutionSnapshot(schemaVersion: "1.0", tripID: trip.id, revision: 2, status: .started, actualStartDate: nil, updatedAt: date("2026-08-15T03:26:04Z"), stopStates: [StopExecution(stopID: "13", status: .completed, updatedAt: date("2026-08-15T03:26:04Z"), updatedByRole: .father, updatedByDeviceID: "device")])
        let pending = PendingExecutionAction(id: UUID(), tripID: trip.id, stopID: "14", action: .complete, occurredAt: date("2026-08-15T03:30:00Z"), createdAt: date("2026-08-15T03:30:00Z"), deviceID: "device", bindingVersion: 1)
        let state = TripExecutionState(trip: trip, snapshot: snapshot, pendingActions: [pending])
        XCTAssertEqual(state.status(for: day.stops[1]), .completed)
        XCTAssertEqual(state.status(for: day.stops[2]), .completed)
        XCTAssertEqual(state.nextStop(on: day)?.id, "15")
        XCTAssertEqual(state.progress(for: day), TripDayProgress(completed: 2, resolved: 2, total: 4))
    }

    func testPendingResolutionDistinguishesRetryableResolvedAndInvalid() throws {
        let trip = try fixtureTrip()
        let day = trip.days[0]
        let snapshot = TripExecutionSnapshot(schemaVersion: "1.0", tripID: trip.id, revision: 2, status: .planning, actualStartDate: nil, updatedAt: date("2026-08-15T03:26:04Z"), stopStates: [StopExecution(stopID: "13", status: .completed, updatedAt: date("2026-08-15T03:26:04Z"), updatedByRole: .father, updatedByDeviceID: "device")])
        let resolved = action(stopID: "13", action: .skip)
        let retryable = action(stopID: "14", action: .complete)
        let invalid = action(stopID: "15", action: .skip)
        let state = TripExecutionState(trip: trip, snapshot: snapshot, pendingActions: [])
        XCTAssertEqual(state.resolution(of: resolved), .resolvedNoOp)
        XCTAssertEqual(state.resolution(of: retryable), .retryable)
        XCTAssertEqual(state.resolution(of: invalid), .invalid)
        XCTAssertEqual(state.resolution(of: PendingExecutionAction(id: UUID(), tripID: trip.id, stopID: nil, action: .start, occurredAt: date("2026-08-15T03:20:00Z"), createdAt: date("2026-08-15T03:20:00Z"), deviceID: "device", bindingVersion: 1)), .retryable)
        XCTAssertEqual(day.stops.count, 5)
    }

    func testConfirmedCompletionAndStartedLifecycleResolveSupersededIntents() throws {
        let trip = try fixtureTrip()
        let completedStop = trip.days[0].stops[1]
        let snapshot = TripExecutionSnapshot(
            schemaVersion: "1.0", tripID: trip.id, revision: 4, status: .started,
            actualStartDate: nil, updatedAt: date("2026-08-15T03:26:04Z"), stopStates: [
                StopExecution(stopID: completedStop.id, status: .completed,
                              updatedAt: date("2026-08-15T03:26:04Z"), updatedByRole: .father,
                              updatedByDeviceID: "device")
            ])
        let state = TripExecutionState(trip: trip, snapshot: snapshot, pendingActions: [])

        XCTAssertEqual(state.resolution(of: action(stopID: completedStop.id, action: .arrive)), .resolvedNoOp)
        XCTAssertEqual(state.resolution(of: action(stopID: completedStop.id, action: .complete)), .resolvedNoOp)
        XCTAssertEqual(state.resolution(of: action(stopID: completedStop.id, action: .skip)), .resolvedNoOp)
        XCTAssertEqual(
            state.resolution(of: PendingExecutionAction(
                id: UUID(), tripID: trip.id, stopID: nil, action: .start,
                occurredAt: date("2026-08-15T03:20:00Z"), createdAt: date("2026-08-15T03:20:00Z"),
                deviceID: "device", bindingVersion: 1
            )),
            .resolvedNoOp
        )
    }

    func testPendingActionsUseCreatedAtOrderAndIgnoreIllegalTransitions() throws {
        let trip = try fixtureTrip()
        let day = trip.days[0]
        let stop = day.stops[1]
        let arrive = action(stopID: stop.id, action: .arrive, createdAt: "2026-08-15T03:21:00Z")
        let complete = action(stopID: stop.id, action: .complete, createdAt: "2026-08-15T03:22:00Z")
        let illegalRegression = action(stopID: stop.id, action: .arrive, createdAt: "2026-08-15T03:23:00Z")
        let state = TripExecutionState(
            trip: trip,
            snapshot: nil,
            pendingActions: [complete, illegalRegression, arrive]
        )

        XCTAssertEqual(state.status(for: stop), .completed)
    }

    func testArrivedRemainsNextStopWhileMovedIsExcluded() throws {
        let trip = try fixtureTrip()
        let day = trip.days[0]
        let snapshot = TripExecutionSnapshot(
            schemaVersion: "1.0", tripID: trip.id, revision: 3, status: .started,
            actualStartDate: nil, updatedAt: date("2026-08-15T03:26:04Z"), stopStates: [
                StopExecution(stopID: day.stops[1].id, status: .arrived,
                              updatedAt: date("2026-08-15T03:26:04Z"), updatedByRole: .father,
                              updatedByDeviceID: "device"),
                StopExecution(stopID: day.stops[2].id, status: .moved,
                              updatedAt: date("2026-08-15T03:26:04Z"), updatedByRole: .father,
                              updatedByDeviceID: "device")
            ])
        let state = TripExecutionState(trip: trip, snapshot: snapshot, pendingActions: [])

        XCTAssertEqual(state.nextStop(on: day)?.id, day.stops[1].id)
    }

    func testSkippedOptionalStopCanBeCompletedButMovedIntentIsInvalid() throws {
        let trip = try fixtureTrip()
        let day = trip.days[0]
        let optionalStop = day.stops.first(where: \.isOptional)!
        let movedStop = day.stops[2]
        let snapshot = TripExecutionSnapshot(
            schemaVersion: "1.0", tripID: trip.id, revision: 3, status: .started,
            actualStartDate: nil, updatedAt: date("2026-08-15T03:26:04Z"), stopStates: [
                StopExecution(stopID: optionalStop.id, status: .skipped,
                              updatedAt: date("2026-08-15T03:26:04Z"), updatedByRole: .father,
                              updatedByDeviceID: "device"),
                StopExecution(stopID: movedStop.id, status: .moved,
                              updatedAt: date("2026-08-15T03:26:04Z"), updatedByRole: .father,
                              updatedByDeviceID: "device")
            ])
        let state = TripExecutionState(trip: trip, snapshot: snapshot, pendingActions: [])

        XCTAssertEqual(state.resolution(of: action(stopID: optionalStop.id, action: .complete)), .retryable)
        XCTAssertEqual(state.resolution(of: action(stopID: movedStop.id, action: .arrive)), .invalid)
    }

    func testProgressExcludesOriginAndCountsSkippedAsResolvedOnly() throws {
        let trip = try fixtureTrip()
        let day = trip.days[0]
        let snapshot = TripExecutionSnapshot(
            schemaVersion: "1.0", tripID: trip.id, revision: 3, status: .started,
            actualStartDate: nil, updatedAt: date("2026-08-15T03:26:04Z"), stopStates: [
                StopExecution(stopID: day.stops[1].id, status: .skipped,
                              updatedAt: date("2026-08-15T03:26:04Z"), updatedByRole: .father,
                              updatedByDeviceID: "device"),
                StopExecution(stopID: day.stops[2].id, status: .completed,
                              updatedAt: date("2026-08-15T03:26:04Z"), updatedByRole: .father,
                              updatedByDeviceID: "device")
            ])
        let state = TripExecutionState(trip: trip, snapshot: snapshot, pendingActions: [])

        XCTAssertEqual(state.progress(for: day), TripDayProgress(completed: 1, resolved: 2, total: 4))
        XCTAssertEqual(state.nextStop(on: day)?.id, day.stops[3].id)
    }

    private func action(stopID: String, action: ExecutionAction, createdAt: String = "2026-08-15T03:20:00Z") -> PendingExecutionAction {
        PendingExecutionAction(id: UUID(), tripID: "qinggan-2026-family", stopID: stopID, action: action, occurredAt: date("2026-08-15T03:20:00Z"), createdAt: date(createdAt), deviceID: "device", bindingVersion: 1)
    }

    private func fixtureTrip() throws -> Trip {
        let json = """
        {"schemaVersion":"1.0","tripId":"qinggan-2026-family","tripName":"执行测试","startDate":"2026-08-13","endDate":"2026-08-13","durationDays":1,"status":"PLANNING","timeZone":"Asia/Shanghai","revision":1,"updatedAt":"2026-08-11T00:00:00Z","days":[{"id":"5","number":5,"date":"2026-08-17","title":"执行日","type":"MIXED","origin":{"id":"origin","name":"出发地","type":"OVERNIGHT","city":null},"destination":{"id":"destination","name":"终点","type":"CITY","city":null},"stops":[{"id":"12","sequence":1,"type":"ORIGIN","optional":false,"status":"PLANNED","place":{"id":"origin","name":"出发地","type":"OVERNIGHT","city":null}},{"id":"13","sequence":2,"type":"SCENIC","optional":true,"status":"PLANNED","place":{"id":"13","name":"可选点","type":"SCENIC","city":null}},{"id":"14","sequence":3,"type":"SCENIC","optional":false,"status":"PLANNED","place":{"id":"14","name":"必经点","type":"SCENIC","city":null}},{"id":"15","sequence":4,"type":"TRANSFER","optional":false,"status":"PLANNED","place":{"id":"15","name":"中转点","type":"TRANSFER","city":null}},{"id":"16","sequence":5,"type":"SCENIC","optional":false,"status":"PLANNED","place":{"id":"16","name":"终点景点","type":"SCENIC","city":null}}]}]}
        """
        return try TripDecoder().decode(data: Data(json.utf8))
    }

    private func date(_ value: String) -> Date {
        TripDateCodec.timestampDate(from: value)!
    }
}
