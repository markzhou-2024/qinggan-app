import Foundation

public enum RuntimeBuildMode: Equatable, Sendable {
    case debug
    case release
}

public enum RuntimeConfigurationError: Error, Equatable, Sendable {
    case missingProductionAPIBaseURL
    case insecureProductionAPIBaseURL
    case invalidAPIBaseURL
}

public struct ResolvedRuntimeConfiguration: Equatable, Sendable {
    public let apiBaseURL: URL?
    public let allowsFixtureFallback: Bool

    public init(apiBaseURL: URL?, allowsFixtureFallback: Bool) {
        self.apiBaseURL = apiBaseURL
        self.allowsFixtureFallback = allowsFixtureFallback
    }
}

public enum RuntimeConfigurationPolicy {
    public static func resolve(
        infoAPIBaseURL: String?,
        environmentAPIBaseURL: String?,
        buildMode: RuntimeBuildMode
    ) throws -> ResolvedRuntimeConfiguration {
        switch buildMode {
        case .debug:
            let rawURL = normalized(environmentAPIBaseURL) ?? normalized(infoAPIBaseURL)
            return ResolvedRuntimeConfiguration(
                apiBaseURL: try rawURL.map(validURL(from:)),
                allowsFixtureFallback: true
            )

        case .release:
            guard let rawURL = normalized(infoAPIBaseURL) else {
                throw RuntimeConfigurationError.missingProductionAPIBaseURL
            }
            let url = try validURL(from: rawURL)
            guard url.scheme?.lowercased() == "https" else {
                throw RuntimeConfigurationError.insecureProductionAPIBaseURL
            }
            return ResolvedRuntimeConfiguration(apiBaseURL: url, allowsFixtureFallback: false)
        }
    }

    private static func normalized(_ value: String?) -> String? {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        return trimmed
    }

    private static func validURL(from rawValue: String) throws -> URL {
        guard let url = URL(string: rawValue), url.scheme != nil, url.host != nil else {
            throw RuntimeConfigurationError.invalidAPIBaseURL
        }
        return url
    }
}
