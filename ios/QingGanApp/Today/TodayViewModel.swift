import Foundation
import Observation
import QingGanCore

@MainActor
@Observable
final class TodayViewModel {
    enum State: Equatable { case loading; case ready(TodaySnapshot); case unavailable(String) }
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
            let trip = try await repository.itinerary()
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = TimeZone(identifier: "Asia/Shanghai")!
            guard let day = currentDayResolver.execute(trip: trip, on: now, calendar: calendar).day else {
                state = .unavailable("今天不在此行程日期内。")
                return
            }
            let nextStop = nextStopResolver.execute(stops: day.stops)
            state = .ready(TodaySnapshot(
                day: day,
                nextStop: nextStop,
                recommendedNavigationPoint: nextStop.flatMap { navigationPointResolver.execute(points: $0.navigationPoints) },
                tonightStay: tonightResolver.execute(day: day)
            ))
        } catch { state = .unavailable(error.localizedDescription) }
    }
}

struct TodaySnapshot: Equatable {
    let day: TripDay
    let nextStop: TripStop?
    let recommendedNavigationPoint: NavigationPoint?
    let tonightStay: Stay?
}
