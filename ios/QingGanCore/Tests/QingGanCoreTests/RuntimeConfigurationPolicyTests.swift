import Foundation
import XCTest
@testable import QingGanCore

final class RuntimeConfigurationPolicyTests: XCTestCase {
    func testReleaseRequiresConfiguredHTTPSAPIBaseURL() throws {
        let result = try RuntimeConfigurationPolicy.resolve(
            infoAPIBaseURL: "https://qinggan.findagent.tech",
            environmentAPIBaseURL: nil,
            buildMode: .release
        )

        XCTAssertEqual(result.apiBaseURL, URL(string: "https://qinggan.findagent.tech"))
        XCTAssertFalse(result.allowsFixtureFallback)
    }

    func testReleaseRejectsMissingAPIBaseURLInsteadOfFallingBackToFixture() {
        XCTAssertThrowsError(
            try RuntimeConfigurationPolicy.resolve(
                infoAPIBaseURL: nil,
                environmentAPIBaseURL: nil,
                buildMode: .release
            )
        ) { error in
            XCTAssertEqual(error as? RuntimeConfigurationError, .missingProductionAPIBaseURL)
        }
    }

    func testReleaseRejectsNonHTTPSAPIBaseURL() {
        XCTAssertThrowsError(
            try RuntimeConfigurationPolicy.resolve(
                infoAPIBaseURL: "http://101.35.247.243:8080",
                environmentAPIBaseURL: nil,
                buildMode: .release
            )
        ) { error in
            XCTAssertEqual(error as? RuntimeConfigurationError, .insecureProductionAPIBaseURL)
        }
    }

    func testDebugAllowsEnvironmentOverrideForLocalDevelopment() throws {
        let result = try RuntimeConfigurationPolicy.resolve(
            infoAPIBaseURL: "https://qinggan.findagent.tech",
            environmentAPIBaseURL: "http://127.0.0.1:8080",
            buildMode: .debug
        )

        XCTAssertEqual(result.apiBaseURL, URL(string: "http://127.0.0.1:8080"))
        XCTAssertTrue(result.allowsFixtureFallback)
    }

    func testDebugWithoutConfiguredURLAllowsFixtureFallback() throws {
        let result = try RuntimeConfigurationPolicy.resolve(
            infoAPIBaseURL: nil,
            environmentAPIBaseURL: nil,
            buildMode: .debug
        )

        XCTAssertNil(result.apiBaseURL)
        XCTAssertTrue(result.allowsFixtureFallback)
    }
}
