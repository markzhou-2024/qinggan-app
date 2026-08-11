import Foundation
import QingGanCore

struct AppRuntimeConfiguration: Equatable, Sendable {
    let apiBaseURL: URL?
    let tripID: String
    let allowsFixtureFallback: Bool

    static func resolve(
        infoDictionary: [String: Any],
        environment: [String: String],
        buildMode: RuntimeBuildMode
    ) throws -> AppRuntimeConfiguration {
        let infoAPIBaseURL = normalized(infoDictionary["QINGGAN_API_BASE_URL"] as? String)
        let environmentAPIBaseURL = buildMode == .debug
            ? normalized(environment["QINGGAN_API_BASE_URL"])
            : nil

        let resolved = try RuntimeConfigurationPolicy.resolve(
            infoAPIBaseURL: infoAPIBaseURL,
            environmentAPIBaseURL: environmentAPIBaseURL,
            buildMode: buildMode
        )

        let infoTripID = normalized(infoDictionary["QINGGAN_TRIP_ID"] as? String)
        let environmentTripID = buildMode == .debug
            ? normalized(environment["QINGGAN_TRIP_ID"])
            : nil

        return AppRuntimeConfiguration(
            apiBaseURL: resolved.apiBaseURL,
            tripID: environmentTripID ?? infoTripID ?? "qinggan-2026-family",
            allowsFixtureFallback: resolved.allowsFixtureFallback
        )
    }

    static func current(
        bundle: Bundle = .main,
        processInfo: ProcessInfo = .processInfo
    ) throws -> AppRuntimeConfiguration {
        #if DEBUG
        let buildMode: RuntimeBuildMode = .debug
        #else
        let buildMode: RuntimeBuildMode = .release
        #endif

        return try resolve(
            infoDictionary: bundle.infoDictionary ?? [:],
            environment: processInfo.environment,
            buildMode: buildMode
        )
    }

    private static func normalized(_ value: String?) -> String? {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines),
              !trimmed.isEmpty,
              !trimmed.hasPrefix("$(") else {
            return nil
        }
        return trimmed
    }
}
