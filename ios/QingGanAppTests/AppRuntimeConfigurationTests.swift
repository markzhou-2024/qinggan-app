import Foundation
import XCTest
@testable import QingGanApp

final class AppRuntimeConfigurationTests: XCTestCase {
    func testReleaseUsesInfoPlistProductionURLAndDisablesFixtureFallback() throws {
        let configuration = try AppRuntimeConfiguration.resolve(
            infoDictionary: [
                "QINGGAN_API_BASE_URL": "https://qinggan.findagent.tech",
                "QINGGAN_TRIP_ID": "qinggan-2026-family"
            ],
            environment: [:],
            buildMode: .release
        )

        XCTAssertEqual(configuration.apiBaseURL, URL(string: "https://qinggan.findagent.tech"))
        XCTAssertEqual(configuration.tripID, "qinggan-2026-family")
        XCTAssertFalse(configuration.allowsFixtureFallback)
    }

    func testReleaseWithoutProductionURLThrowsInsteadOfUsingFixture() {
        XCTAssertThrowsError(
            try AppRuntimeConfiguration.resolve(
                infoDictionary: ["QINGGAN_TRIP_ID": "qinggan-2026-family"],
                environment: [:],
                buildMode: .release
            )
        )
    }

    func testDebugEnvironmentOverridesInfoPlistForLocalDevelopment() throws {
        let configuration = try AppRuntimeConfiguration.resolve(
            infoDictionary: [
                "QINGGAN_API_BASE_URL": "https://qinggan.findagent.tech",
                "QINGGAN_TRIP_ID": "qinggan-2026-family"
            ],
            environment: [
                "QINGGAN_API_BASE_URL": "http://127.0.0.1:8080",
                "QINGGAN_TRIP_ID": "debug-trip"
            ],
            buildMode: .debug
        )

        XCTAssertEqual(configuration.apiBaseURL, URL(string: "http://127.0.0.1:8080"))
        XCTAssertEqual(configuration.tripID, "debug-trip")
        XCTAssertTrue(configuration.allowsFixtureFallback)
    }

    func testFactoryUsesFixtureOnlyWhenConfigurationExplicitlyAllowsIt() async throws {
        let configuration = AppRuntimeConfiguration(
            apiBaseURL: nil,
            tripID: "qinggan-2026-family",
            allowsFixtureFallback: true
        )

        let trip = try await AppTripRepositoryFactory.make(configuration: configuration).itinerary()

        XCTAssertEqual(trip.id, "qinggan-2026-family")
    }

    func testFactoryWithoutURLAndWithoutFixturePermissionFailsExplicitly() async {
        let configuration = AppRuntimeConfiguration(
            apiBaseURL: nil,
            tripID: "qinggan-2026-family",
            allowsFixtureFallback: false
        )

        do {
            _ = try await AppTripRepositoryFactory.make(configuration: configuration).itinerary()
            XCTFail("Expected a production configuration failure instead of fixture data")
        } catch {
            XCTAssertTrue(error.localizedDescription.contains("生产服务器"))
        }
    }
}
