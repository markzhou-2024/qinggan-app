import Foundation
import QingGanCore

enum AppTripRepositoryFactory {
    static func make() -> any TripRepository {
        guard let rawBaseURL = ProcessInfo.processInfo.environment["QINGGAN_API_BASE_URL"],
              let baseURL = URL(string: rawBaseURL) else {
            return AppFixtureTripRepository()
        }
        let tripID = ProcessInfo.processInfo.environment["QINGGAN_TRIP_ID"] ?? "qinggan-2026-family"
        return CachedTripRepository(
            remote: URLSessionItineraryDataSource(baseURL: baseURL, tripID: tripID),
            store: FileTripStore()
        )
    }
}

struct URLSessionItineraryDataSource: ItineraryRemoteDataSource {
    let baseURL: URL
    let tripID: String
    private let session: URLSession = .shared

    func fetchItinerary() async throws -> Data {
        let url = baseURL
            .appending(path: "api")
            .appending(path: "v1")
            .appending(path: "trips")
            .appending(path: tripID)
            .appending(path: "itinerary")
        let (data, response) = try await session.data(from: url)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        return data
    }
}
