import Foundation
import XCTest
@testable import QingGanCore

final class CacheAndNavigationTests: XCTestCase {
    func testCachedRepositorySavesValidatedRemotePayloadBeforeReturningTrip() async throws {
        let fixture = try fixtureData(named: "qinggan-itinerary")
        let remote = StubRemoteDataSource(result: .success(fixture))
        let store = MemoryTripStore()
        let repository = CachedTripRepository(remote: remote, store: store)

        let trip = try await repository.itinerary()
        let saved = await store.savedData()

        XCTAssertEqual(trip.id, "qinggan-2026-family")
        XCTAssertEqual(saved, fixture)
    }

    func testCachedRepositoryFallsBackToLatestValidCacheWhenRemoteFails() async throws {
        let fixture = try fixtureData(named: "qinggan-itinerary")
        let remote = StubRemoteDataSource(result: .failure(StubError.offline))
        let store = MemoryTripStore(initialData: fixture)
        let repository = CachedTripRepository(remote: remote, store: store)

        let trip = try await repository.itinerary()

        XCTAssertEqual(trip.name, "青甘大环线10天自驾")
    }

    func testMalformedRemotePayloadIsNotSavedAndDoesNotReplaceTheValidCache() async throws {
        let fixture = try fixtureData(named: "qinggan-itinerary")
        let remote = StubRemoteDataSource(result: .success(Data("not-json".utf8)) )
        let store = MemoryTripStore(initialData: fixture)
        let repository = CachedTripRepository(remote: remote, store: store)

        let trip = try await repository.itinerary()
        let saved = await store.savedData()

        XCTAssertEqual(trip.id, "qinggan-2026-family")
        XCTAssertEqual(saved, fixture)
    }

    func testAppleMapsUsesOnlyWGS84Coordinates() throws {
        let point = navigationPoint(primary: GeoCoordinate(latitude: 37.1234, longitude: 97.5678, system: .wgs84))
        let service = MapURLNavigationService(canOpenURL: { _ in false })

        let url = try service.navigationURL(for: point, provider: .appleMaps)

        XCTAssertEqual(url.host, "maps.apple.com")
        XCTAssertTrue(url.absoluteString.contains("daddr=37.1234,97.5678"))
    }

    func testAppleMapsUsesNameAndAddressFallbackInsteadOfMisusingGCJ02Coordinates() throws {
        let point = navigationPoint(primary: GeoCoordinate(latitude: 37.1234, longitude: 97.5678, system: .gcj02))
        let service = MapURLNavigationService(canOpenURL: { _ in false })

        let url = try service.navigationURL(for: point, provider: .appleMaps)

        XCTAssertFalse(url.absoluteString.contains("37.1234"))
        XCTAssertTrue(url.absoluteString.removingPercentEncoding?.contains("翡翠湖") == true)
    }

    func testAvailableProvidersIncludesInstalledThirdPartyMapsAndAppleFallback() {
        let point = navigationPoint(primary: GeoCoordinate(latitude: 37.1234, longitude: 97.5678, system: .gcj02))
        let service = MapURLNavigationService(canOpenURL: { $0.scheme == "iosamap" })

        XCTAssertEqual(service.availableProviders(for: point), [.appleMaps, .amap])
    }

    func testAMapRejectsBD09CoordinatesThatHaveNoDeclaredCompatibility() {
        let point = navigationPoint(primary: GeoCoordinate(latitude: 37.1234, longitude: 97.5678, system: .bd09))
        let service = MapURLNavigationService(canOpenURL: { _ in true })

        XCTAssertThrowsError(try service.navigationURL(for: point, provider: .amap)) { error in
            XCTAssertEqual(error as? NavigationServiceError, .incompatibleCoordinate(provider: .amap))
        }
    }

    func testBaiduNavigationKeepsTheDeclaredCoordinateSystem() throws {
        let point = navigationPoint(primary: GeoCoordinate(latitude: 37.1234, longitude: 97.5678, system: .gcj02))
        let service = MapURLNavigationService(canOpenURL: { _ in true })

        let url = try service.navigationURL(for: point, provider: .baiduMaps)

        XCTAssertEqual(url.scheme, "baidumap")
        XCTAssertTrue(url.absoluteString.contains("coord_type=gcj02"))
    }

    private func fixtureData(named name: String) throws -> Data {
        let url = try XCTUnwrap(Bundle.module.url(forResource: name, withExtension: "json"))
        return try Data(contentsOf: url)
    }

    private func navigationPoint(primary: GeoCoordinate?) -> NavigationPoint {
        NavigationPoint(
            id: "emerald-parking",
            name: "翡翠湖景区停车场",
            address: "大柴旦翡翠湖",
            type: .parking,
            note: nil,
            navigationKeyword: "大柴旦翡翠湖景区停车场",
            isRecommended: true,
            verificationStatus: .verified,
            primaryCoordinate: primary,
            alternateCoordinates: []
        )
    }
}

private enum StubError: Error {
    case offline
}

private struct StubRemoteDataSource: ItineraryRemoteDataSource {
    let result: Result<Data, Error>

    func fetchItinerary() async throws -> Data {
        try result.get()
    }
}

private actor MemoryTripStore: LocalTripStore {
    private var data: Data?

    init(initialData: Data? = nil) {
        data = initialData
    }

    func save(data: Data) async throws {
        self.data = data
    }

    func load() async throws -> Data? {
        data
    }

    func savedData() -> Data? {
        data
    }
}
