import Foundation

public enum TripPhase: String, Equatable, Sendable {
    case preTrip
    case inTrip
    case postTrip
}

public struct CurrentTripDay: Equatable, Sendable {
    public let day: TripDay?
    public let phase: TripPhase

    public init(day: TripDay?, phase: TripPhase = .inTrip) {
        self.day = day
        self.phase = phase
    }
}

public struct ResolveCurrentTripDayUseCase: Sendable {
    public init() {}

    public func execute(trip: Trip, on date: Date, calendar: Calendar = .current) -> CurrentTripDay {
        var travelCalendar = calendar
        travelCalendar.timeZone = TimeZone(identifier: trip.timeZoneIdentifier) ?? TimeZone(identifier: "Asia/Shanghai")!
        let start = travelCalendar.startOfDay(for: trip.effectiveStartDate)
        let today = travelCalendar.startOfDay(for: date)
        let offset = travelCalendar.dateComponents([.day], from: start, to: today).day ?? 0
        guard offset >= 0 else { return CurrentTripDay(day: nil, phase: .preTrip) }
        guard offset < trip.durationDays else { return CurrentTripDay(day: nil, phase: .postTrip) }
        let dayNumber = offset + 1
        let dateComponents = travelCalendar.dateComponents([.year, .month, .day], from: today)
        let canonicalDate = dateComponents.year.flatMap { year in
            dateComponents.month.flatMap { month in
                dateComponents.day.flatMap { day in
                    TripDateCodec.day.date(from: String(format: "%04d-%02d-%02d", year, month, day))
                }
            }
        }
        let resolvedDate = canonicalDate ?? today
        let day = trip.days.first(where: { $0.number == dayNumber })?.dated(resolvedDate)
        return CurrentTripDay(day: day, phase: .inTrip)
    }
}

public struct TripRuntimeConfiguration: Equatable, Sendable {
    public let plannedStartDate: Date
    public let actualStartDate: Date?
    public let status: TripLifecycleStatus
    public let timeZoneIdentifier: String

    public init(plannedStartDate: Date, actualStartDate: Date?, status: TripLifecycleStatus, timeZoneIdentifier: String = "Asia/Shanghai") {
        self.plannedStartDate = plannedStartDate
        self.actualStartDate = actualStartDate
        self.status = status
        self.timeZoneIdentifier = timeZoneIdentifier
    }

    public var effectiveStartDate: Date { actualStartDate ?? plannedStartDate }
}

public struct UpdatePlannedStartDateUseCase: Sendable {
    public init() {}

    public func execute(current: TripRuntimeConfiguration, plannedStartDate: Date) -> TripRuntimeConfiguration {
        TripRuntimeConfiguration(plannedStartDate: plannedStartDate, actualStartDate: current.actualStartDate,
                                 status: current.status, timeZoneIdentifier: current.timeZoneIdentifier)
    }
}

public struct StartTripUseCase: Sendable {
    public init() {}

    public func execute(current: TripRuntimeConfiguration, actualStartDate: Date) -> TripRuntimeConfiguration {
        TripRuntimeConfiguration(plannedStartDate: current.plannedStartDate, actualStartDate: actualStartDate,
                                 status: .started, timeZoneIdentifier: current.timeZoneIdentifier)
    }
}

public struct ResolveNextStopUseCase: Sendable {
    public init() {}

    public func execute(stops: [TripStop]) -> TripStop? {
        stops
            .sorted { $0.sequence < $1.sequence }
            .first { $0.type != .origin && $0.status != .completed && $0.status != .skipped }
    }
}

public struct ResolveRecommendedNavigationPointUseCase: Sendable {
    public init() {}

    public func execute(points: [NavigationPoint]) -> NavigationPoint? {
        points.first(where: \.isRecommended) ?? points.first
    }
}

public struct ResolveTonightStayUseCase: Sendable {
    public init() {}

    public func execute(day: TripDay) -> Stay? {
        day.stay
    }
}
