import Foundation
import QingGanCore

struct AppFixtureTripRepository: TripRepository, TripDataOriginReporting {
    func itinerary() async throws -> Trip {
        try await itineraryWithOrigin().trip
    }

    func itineraryWithOrigin() async throws -> TripFetchResult {
        guard let url = Bundle.main.url(forResource: "qinggan-itinerary", withExtension: "json") else {
            throw AppFixtureError.missingItineraryFixture
        }
        let trip = try await FixtureTripRepository(data: Data(contentsOf: url)).itinerary()
        return TripFetchResult(trip: trip, origin: .fixture)
    }
}

enum AppFixtureError: LocalizedError {
    case missingItineraryFixture

    var errorDescription: String? {
        "未找到预置行程数据。"
    }
}

struct AppDateProvider {
    private let provider: any DateProvider

    init(arguments: [String] = ProcessInfo.processInfo.arguments) {
        if let index = arguments.firstIndex(of: "-QingGanToday"), arguments.indices.contains(index + 1),
           let date = TripDateCodec.day.date(from: arguments[index + 1]) {
            provider = FixedDateProvider(date: date)
        } else {
            provider = SystemDateProvider()
        }
    }

    func now() -> Date { provider.now() }
}
