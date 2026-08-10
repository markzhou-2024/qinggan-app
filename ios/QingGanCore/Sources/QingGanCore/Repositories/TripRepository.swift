import Foundation

public protocol TripRepository {
    func itinerary() async throws -> Trip
}

public struct TripDecoder: Sendable {
    public init() {}

    public func decode(data: Data) throws -> Trip {
        try JSONDecoder().decode(Trip.self, from: data)
    }
}

public struct FixtureTripRepository: TripRepository {
    private let data: Data
    private let decoder: TripDecoder

    public init(data: Data, decoder: TripDecoder = TripDecoder()) {
        self.data = data
        self.decoder = decoder
    }

    public func itinerary() async throws -> Trip {
        try decoder.decode(data: data)
    }
}
