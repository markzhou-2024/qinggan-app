import Foundation
import QingGanCore

enum AppTripRepositoryFactory {
    static func make() -> any TripRepository {
        do {
            return make(configuration: try AppRuntimeConfiguration.current())
        } catch {
            return ConfigurationFailureTripRepository(message: configurationMessage(for: error))
        }
    }

    static func make(configuration: AppRuntimeConfiguration) -> any TripRepository {
        if let baseURL = configuration.apiBaseURL {
            return CachedTripRepository(
                remote: URLSessionItineraryDataSource(baseURL: baseURL, tripID: configuration.tripID),
                store: FileTripStore()
            )
        }

        if configuration.allowsFixtureFallback {
            return AppFixtureTripRepository()
        }

        return ConfigurationFailureTripRepository(message: "生产服务器未配置，无法加载行程。")
    }

    private static func configurationMessage(for error: Error) -> String {
        switch error as? RuntimeConfigurationError {
        case .missingProductionAPIBaseURL:
            return "生产服务器地址未配置，无法加载行程。"
        case .insecureProductionAPIBaseURL:
            return "生产服务器必须使用 HTTPS。"
        case .invalidAPIBaseURL:
            return "生产服务器地址格式无效。"
        case nil:
            return "生产运行配置无效：\(error.localizedDescription)"
        }
    }
}

private struct ConfigurationFailureTripRepository: TripRepository {
    let message: String

    func itinerary() async throws -> Trip {
        throw ConfigurationFailure(message: message)
    }
}

private struct ConfigurationFailure: LocalizedError, Sendable {
    let message: String

    var errorDescription: String? { message }
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
