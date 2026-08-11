import Foundation
import Observation
import QingGanCore

@MainActor
@Observable
final class TodayViewModel {
    enum State: Equatable {
        case loading
        case preTrip(trip: Trip, daysUntilStart: Int)
        case ready(TodaySnapshot)
        case postTrip(Trip)
        case unavailable(String)
    }
    private let repository: any TripRepository
    private let currentDayResolver = ResolveCurrentTripDayUseCase()
    private let nextStopResolver = ResolveNextStopUseCase()
    private let navigationPointResolver = ResolveRecommendedNavigationPointUseCase()
    private let tonightResolver = ResolveTonightStayUseCase()
    private(set) var state: State = .loading

    init(repository: any TripRepository) { self.repository = repository }

    func load(now: Date = Date()) async {
        state = .loading
        do {
            let result: TripFetchResult
            if let reportingRepository = repository as? any TripDataOriginReporting {
                result = try await reportingRepository.itineraryWithOrigin()
            } else {
                result = TripFetchResult(trip: try await repository.itinerary(), origin: .fixture)
            }
            let trip = result.trip
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = TimeZone(identifier: "Asia/Shanghai")!
            let current = currentDayResolver.execute(trip: trip, on: now, calendar: calendar)
            if current.phase == .preTrip {
                let days = max(0, calendar.dateComponents([.day], from: calendar.startOfDay(for: now), to: calendar.startOfDay(for: trip.effectiveStartDate)).day ?? 0)
                state = .preTrip(trip: trip, daysUntilStart: days)
                return
            }
            if current.phase == .postTrip {
                state = .postTrip(trip)
                return
            }
            guard let day = current.day else {
                state = .unavailable("今天不在此行程日期内。")
                return
            }
            let nextStop = nextStopResolver.execute(stops: day.stops)
            state = .ready(TodaySnapshot(
                day: day,
                nextStop: nextStop,
                recommendedNavigationPoint: nextStop.flatMap { navigationPointResolver.execute(points: $0.navigationPoints) },
                tonightStay: tonightResolver.execute(day: day),
                dataOrigin: result.origin
            ))
        } catch { state = .unavailable(error.localizedDescription) }
    }
}

struct TodaySnapshot: Equatable {
    let day: TripDay
    let nextStop: TripStop?
    let recommendedNavigationPoint: NavigationPoint?
    let tonightStay: Stay?
    let dataOrigin: TripDataOrigin
}
