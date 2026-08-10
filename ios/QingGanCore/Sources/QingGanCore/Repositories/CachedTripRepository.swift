import Foundation

public protocol ItineraryRemoteDataSource: Sendable {
    func fetchItinerary() async throws -> Data
}

public protocol LocalTripStore: Sendable {
    func save(data: Data) async throws
    func load() async throws -> Data?
}

public enum TripDataOrigin: Equatable, Sendable {
    case remote
    case cache
    case fixture
}

public struct TripFetchResult: Equatable, Sendable {
    public let trip: Trip
    public let origin: TripDataOrigin

    public init(trip: Trip, origin: TripDataOrigin) {
        self.trip = trip
        self.origin = origin
    }
}

public protocol TripDataOriginReporting: Sendable {
    func itineraryWithOrigin() async throws -> TripFetchResult
}

public struct RemoteTripRepository: TripRepository {
    private let remote: any ItineraryRemoteDataSource
    private let decoder: TripDecoder

    public init(remote: any ItineraryRemoteDataSource, decoder: TripDecoder = TripDecoder()) {
        self.remote = remote
        self.decoder = decoder
    }

    public func itinerary() async throws -> Trip {
        try decoder.decode(data: try await remote.fetchItinerary())
    }
}

/// Remote is authoritative when valid; the last validated document provides weak-network continuity.
public struct CachedTripRepository: TripRepository, TripDataOriginReporting {
    private let remote: any ItineraryRemoteDataSource
    private let store: any LocalTripStore
    private let decoder: TripDecoder

    public init(
        remote: any ItineraryRemoteDataSource,
        store: any LocalTripStore,
        decoder: TripDecoder = TripDecoder()
    ) {
        self.remote = remote
        self.store = store
        self.decoder = decoder
    }

    public func itinerary() async throws -> Trip {
        try await itineraryWithOrigin().trip
    }

    public func itineraryWithOrigin() async throws -> TripFetchResult {
        do {
            let remoteData = try await remote.fetchItinerary()
            let trip = try decoder.decode(data: remoteData)
            try await store.save(data: remoteData)
            return TripFetchResult(trip: trip, origin: .remote)
        } catch {
            guard let cachedData = try await store.load() else {
                throw error
            }
            return TripFetchResult(trip: try decoder.decode(data: cachedData), origin: .cache)
        }
    }
}
