import Foundation
import QingGanCore

struct AppFixtureTripRepository: TripRepository {
    func itinerary() async throws -> Trip {
        guard let url = Bundle.main.url(forResource: "qinggan-itinerary", withExtension: "json") else {
            throw AppFixtureError.missingItineraryFixture
        }
        return try await FixtureTripRepository(data: Data(contentsOf: url)).itinerary()
    }
}

enum AppFixtureError: LocalizedError {
    case missingItineraryFixture

    var errorDescription: String? {
        "未找到预置行程数据。"
    }
}

struct AppDateProvider {
    func now() -> Date {
        let arguments = ProcessInfo.processInfo.arguments
        if let index = arguments.firstIndex(of: "-QingGanToday"), arguments.indices.contains(index + 1),
           let date = TripDateCodec.day.date(from: arguments[index + 1]) {
            return date
        }
        return Date()
    }
}
