import Foundation
import XCTest
@testable import QingGanCore

final class TodayDomainTests: XCTestCase {
    func testFixtureRepositoryDecodesTheCanonicalTenDayItinerary() async throws {
        let repository = FixtureTripRepository(data: try fixtureData(named: "qinggan-itinerary"))

        let trip = try await repository.itinerary()

        XCTAssertEqual(trip.id, "qinggan-2026-family")
        XCTAssertEqual(trip.days.count, 10)
        XCTAssertEqual(trip.days[4].stops.map(\.place.name), ["大柴旦镇", "U型公路", "水上雅丹", "敦煌", "鸣沙山月牙泉"])
    }

    func testCurrentDayResolverReturnsDayFourForAugustSixteenth() throws {
        let trip = try decodedFixture()
        let current = ResolveCurrentTripDayUseCase().execute(
            trip: trip,
            on: try date("2026-08-16"),
            calendar: shanghaiCalendar()
        )

        XCTAssertEqual(current.day?.number, 4)
    }

    func testChangingPlannedStartDateShiftsAllResolvedDayDatesWithoutChangingDayNumbers() throws {
        let trip = try decodedFixture(plannedStartDate: "2026-08-15", actualStartDate: nil)
        let current = ResolveCurrentTripDayUseCase().execute(
            trip: trip,
            on: try date("2026-08-18"),
            calendar: shanghaiCalendar()
        )

        XCTAssertEqual(current.phase, .inTrip)
        XCTAssertEqual(current.day?.number, 4)
        XCTAssertEqual(current.day?.date, try date("2026-08-18"))
        XCTAssertEqual(trip.days.map(\.number), Array(1...10))
    }

    func testCurrentDayResolutionReportsPreTripAndPostTrip() throws {
        let trip = try decodedFixture(plannedStartDate: "2026-08-15", actualStartDate: nil)
        let resolver = ResolveCurrentTripDayUseCase()

        XCTAssertEqual(resolver.execute(trip: trip, on: try date("2026-08-14"), calendar: shanghaiCalendar()).phase, .preTrip)
        XCTAssertEqual(resolver.execute(trip: trip, on: try date("2026-08-25"), calendar: shanghaiCalendar()).phase, .postTrip)
    }

    func testActualStartDateOverridesPlannedStartDate() throws {
        let trip = try decodedFixture(plannedStartDate: "2026-08-13", actualStartDate: "2026-08-15")
        let current = ResolveCurrentTripDayUseCase().execute(
            trip: trip,
            on: try date("2026-08-15"),
            calendar: shanghaiCalendar()
        )

        XCTAssertEqual(trip.effectiveStartDate, try date("2026-08-15"))
        XCTAssertEqual(current.phase, .inTrip)
        XCTAssertEqual(current.day?.number, 1)
    }

    func testStartTripUseCaseLocksEffectiveDateToActualStartDate() throws {
        let configuration = TripRuntimeConfiguration(
            plannedStartDate: try date("2026-08-13"), actualStartDate: nil, status: .planning
        )

        let started = StartTripUseCase().execute(current: configuration, actualStartDate: try date("2026-08-15"))

        XCTAssertEqual(started.status, .started)
        XCTAssertEqual(started.effectiveStartDate, try date("2026-08-15"))
    }

    func testUpdatePlannedStartDateDoesNotRewriteCanonicalDayNumbers() throws {
        let configuration = TripRuntimeConfiguration(
            plannedStartDate: try date("2026-08-13"), actualStartDate: nil, status: .planning
        )

        let updated = UpdatePlannedStartDateUseCase().execute(current: configuration, plannedStartDate: try date("2026-08-15"))

        XCTAssertEqual(updated.plannedStartDate, try date("2026-08-15"))
        XCTAssertNil(updated.actualStartDate)
        XCTAssertEqual(updated.status, .planning)
    }

    func testNextStopResolverSkipsOriginCompletedAndSkippedStops() {
        let origin = stop(id: "origin", name: "茶卡镇", type: .origin, status: .completed)
        let completed = stop(id: "chaka", name: "茶卡天空壹号", type: .scenic, status: .completed)
        let skipped = stop(id: "u-road", name: "U型公路", type: .scenic, status: .skipped)
        let next = stop(id: "emerald", name: "大柴旦翡翠湖", type: .scenic, status: .planned)

        let result = ResolveNextStopUseCase().execute(stops: [origin, completed, skipped, next])

        XCTAssertEqual(result?.place.name, "大柴旦翡翠湖")
    }

    func testNextStopResolverDoesNotNavigateBackToPlannedOrigin() {
        let origin = stop(id: "origin", name: "茶卡镇", type: .origin, status: .planned)
        let next = stop(id: "chaka", name: "茶卡天空壹号", type: .scenic, status: .planned)

        XCTAssertEqual(ResolveNextStopUseCase().execute(stops: [origin, next])?.place.name, "茶卡天空壹号")
    }

    func testRecommendedNavigationResolverUsesRecommendedPointWithoutInventingCoordinates() {
        let pendingParking = NavigationPoint(
            id: "emerald-parking",
            name: "翡翠湖景区停车场",
            address: nil,
            type: .parking,
            note: "推荐导航点待出发前核验",
            navigationKeyword: "大柴旦翡翠湖景区停车场",
            isRecommended: true,
            verificationStatus: .pending,
            primaryCoordinate: nil,
            alternateCoordinates: []
        )

        let result = ResolveRecommendedNavigationPointUseCase().execute(points: [pendingParking])

        XCTAssertEqual(result?.name, "翡翠湖景区停车场")
        XCTAssertNil(result?.primaryCoordinate)
    }

    func testTonightResolverReturnsTheCurrentDaysStay() throws {
        let trip = try decodedFixture()
        let day = trip.days[3]

        XCTAssertEqual(ResolveTonightStayUseCase().execute(day: day)?.hotelName, "大柴旦镇住宿（待确认）")
    }

    func testFixedDateProviderIsExplicitAndSystemProviderIsSeparate() throws {
        let fixedDate = try date("2026-08-16")

        XCTAssertEqual(FixedDateProvider(date: fixedDate).now(), fixedDate)
        XCTAssertNotEqual(SystemDateProvider().now(), fixedDate)
    }

    private func decodedFixture(plannedStartDate: String? = nil, actualStartDate: String? = nil) throws -> Trip {
        var object = try JSONSerialization.jsonObject(with: fixtureData(named: "qinggan-itinerary")) as! [String: Any]
        if let plannedStartDate {
            object["startDate"] = plannedStartDate
            object["endDate"] = "2026-08-24"
        }
        if let actualStartDate { object["actualStartDate"] = actualStartDate }
        object["status"] = "PLANNING"
        object["timeZone"] = "Asia/Shanghai"
        return try TripDecoder().decode(data: JSONSerialization.data(withJSONObject: object))
    }

    private func fixtureData(named name: String) throws -> Data {
        let url = try XCTUnwrap(Bundle.module.url(forResource: name, withExtension: "json"))
        return try Data(contentsOf: url)
    }

    private func date(_ value: String) throws -> Date {
        try XCTUnwrap(TripDateCodec.day.date(from: value))
    }

    private func shanghaiCalendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Shanghai")!
        return calendar
    }

    private func stop(id: String, name: String, type: StopType, status: StopStatus) -> TripStop {
        TripStop(
            id: id,
            sequence: 1,
            type: type,
            priority: nil,
            isOptional: false,
            status: status,
            place: Place(id: id, name: name, type: .scenic, city: nil, priority: nil),
            navigationPoints: []
        )
    }
}
