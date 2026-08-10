import Foundation

public enum NavigationProvider: String, CaseIterable, Equatable, Sendable {
    case appleMaps
    case amap
    case baiduMaps
}

public enum NavigationServiceError: Error, Equatable, Sendable {
    case incompatibleCoordinate(provider: NavigationProvider)
    case unavailableProvider(NavigationProvider)
    case invalidDestination
}

public protocol NavigationService {
    func availableProviders(for point: NavigationPoint) -> [NavigationProvider]
    func navigationURL(for point: NavigationPoint, provider: NavigationProvider) throws -> URL
}

/// URL contracts are intentionally isolated here. UI code only asks for a provider and opens the returned URL.
public struct MapURLNavigationService: NavigationService {
    private let canOpenURL: (URL) -> Bool
    private let sourceApplication: String

    public init(
        sourceApplication: String = "QingGan",
        canOpenURL: @escaping (URL) -> Bool
    ) {
        self.sourceApplication = sourceApplication
        self.canOpenURL = canOpenURL
    }

    public func availableProviders(for point: NavigationPoint) -> [NavigationProvider] {
        var providers: [NavigationProvider] = [.appleMaps]
        if compatibleCoordinate(for: point, provider: .amap) != nil,
           canOpenURL(URL(string: "iosamap://")!) {
            providers.append(.amap)
        }
        if canOpenURL(URL(string: "baidumap://")!) {
            providers.append(.baiduMaps)
        }
        return providers
    }

    public func navigationURL(for point: NavigationPoint, provider: NavigationProvider) throws -> URL {
        switch provider {
        case .appleMaps:
            return try appleMapsURL(for: point)
        case .amap:
            return try amapURL(for: point)
        case .baiduMaps:
            return try baiduMapsURL(for: point)
        }
    }

    private func appleMapsURL(for point: NavigationPoint) throws -> URL {
        var components = URLComponents()
        components.scheme = "https"
        components.host = "maps.apple.com"
        components.path = "/"
        if let coordinate = compatibleCoordinate(for: point, provider: .appleMaps) {
            components.queryItems = [
                URLQueryItem(name: "daddr", value: "\(coordinate.latitude),\(coordinate.longitude)"),
                URLQueryItem(name: "dirflg", value: "d")
            ]
        } else if let query = destinationText(for: point) {
            components.queryItems = [
                URLQueryItem(name: "daddr", value: query),
                URLQueryItem(name: "dirflg", value: "d")
            ]
        } else {
            throw NavigationServiceError.invalidDestination
        }
        guard let url = components.url else { throw NavigationServiceError.invalidDestination }
        return url
    }

    private func amapURL(for point: NavigationPoint) throws -> URL {
        guard let coordinate = compatibleCoordinate(for: point, provider: .amap) else {
            throw NavigationServiceError.incompatibleCoordinate(provider: .amap)
        }
        var components = URLComponents()
        components.scheme = "iosamap"
        components.host = "navi"
        let coordinateAlreadyEncrypted = coordinate.system == .gcj02
        components.queryItems = [
            URLQueryItem(name: "sourceApplication", value: sourceApplication),
            URLQueryItem(name: "poiname", value: point.name),
            URLQueryItem(name: "lat", value: String(coordinate.latitude)),
            URLQueryItem(name: "lon", value: String(coordinate.longitude)),
            URLQueryItem(name: "dev", value: coordinateAlreadyEncrypted ? "0" : "1"),
            URLQueryItem(name: "style", value: "0")
        ]
        guard let url = components.url else { throw NavigationServiceError.invalidDestination }
        return url
    }

    private func baiduMapsURL(for point: NavigationPoint) throws -> URL {
        var components = URLComponents()
        components.scheme = "baidumap"
        components.host = "map"
        components.path = "/navi"
        var items = [
            URLQueryItem(name: "query", value: point.name),
            URLQueryItem(name: "type", value: "DEFAULT"),
            URLQueryItem(name: "src", value: "ios.qinggan.app")
        ]
        if let coordinate = compatibleCoordinate(for: point, provider: .baiduMaps) {
            items.append(URLQueryItem(name: "location", value: "\(coordinate.latitude),\(coordinate.longitude)"))
            items.append(URLQueryItem(name: "coord_type", value: baiduCoordinateType(for: coordinate.system)))
        }
        components.queryItems = items
        guard let url = components.url else { throw NavigationServiceError.invalidDestination }
        return url
    }

    private func compatibleCoordinate(for point: NavigationPoint, provider: NavigationProvider) -> GeoCoordinate? {
        let coordinates = [point.primaryCoordinate].compactMap { $0 } + point.alternateCoordinates
        switch provider {
        case .appleMaps:
            return coordinates.first(where: { $0.system == .wgs84 })
        case .amap:
            return coordinates.first(where: { $0.system == .gcj02 || $0.system == .wgs84 })
        case .baiduMaps:
            return coordinates.first
        }
    }

    private func baiduCoordinateType(for system: CoordinateSystem) -> String {
        switch system {
        case .wgs84: "wgs84"
        case .gcj02: "gcj02"
        case .bd09: "bd09ll"
        }
    }

    private func destinationText(for point: NavigationPoint) -> String? {
        [point.navigationKeyword, point.address, point.name]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first(where: { !$0.isEmpty })
    }
}
