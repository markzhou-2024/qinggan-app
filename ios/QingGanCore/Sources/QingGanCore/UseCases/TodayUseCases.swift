import Foundation

public struct CurrentTripDay: Equatable, Sendable {
    public let day: TripDay?

    public init(day: TripDay?) {
        self.day = day
    }
}

public struct ResolveCurrentTripDayUseCase: Sendable {
    public init() {}

    public func execute(trip: Trip, on date: Date, calendar: Calendar = .current) -> CurrentTripDay {
        let day = trip.days.first { calendar.isDate($0.date, inSameDayAs: date) }
        return CurrentTripDay(day: day)
    }
}

public struct ResolveNextStopUseCase: Sendable {
    public init() {}

    public func execute(stops: [TripStop]) -> TripStop? {
        stops
            .sorted { $0.sequence < $1.sequence }
            .first { $0.status != .completed && $0.status != .skipped }
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
