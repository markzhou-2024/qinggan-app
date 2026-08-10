import Foundation

public protocol NormalizedStringCodable: Codable, RawRepresentable where RawValue == String {}

public extension NormalizedStringCodable {
    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let rawValue = try container.decode(String.self).lowercased()
        guard let value = Self(rawValue: rawValue) else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Unsupported \(Self.self) value: \(rawValue)")
        }
        self = value
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue.uppercased())
    }
}

public enum DayType: String, CaseIterable, NormalizedStringCodable, Sendable {
    case touring
    case transfer
    case longDrive = "long_drive"
    case `return`
    case mixed
}

public enum PlaceType: String, CaseIterable, NormalizedStringCodable, Sendable {
    case city
    case scenic
    case overnight
    case navigation
    case transfer
}

public enum StopType: String, CaseIterable, NormalizedStringCodable, Sendable {
    case origin
    case scenic
    case transfer
    case meal
    case overnight
    case destination
}

public enum StopStatus: String, CaseIterable, NormalizedStringCodable, Sendable {
    case planned
    case arrived
    case completed
    case skipped
    case moved
}

public enum Priority: String, CaseIterable, NormalizedStringCodable, Sendable {
    case sPlus = "s_plus"
    case s
    case aPlus = "a_plus"
    case a
    case b
}

public enum VerificationStatus: String, CaseIterable, NormalizedStringCodable, Sendable {
    case verified
    case pending
}

public enum NavigationPointType: String, CaseIterable, NormalizedStringCodable, Sendable {
    case recommended
    case avoid
    case parking
    case entrance
    case viewpoint
}

public enum CoordinateSystem: String, Codable, CaseIterable, Sendable {
    case wgs84
    case gcj02
    case bd09
}

public struct GeoCoordinate: Codable, Equatable, Sendable {
    public let latitude: Double
    public let longitude: Double
    public let system: CoordinateSystem

    public init(latitude: Double, longitude: Double, system: CoordinateSystem) {
        self.latitude = latitude
        self.longitude = longitude
        self.system = system
    }
}

public struct Place: Identifiable, Codable, Equatable, Sendable {
    public let id: String
    public let name: String
    public let type: PlaceType
    public let city: String?
    public let priority: Priority?

    public init(id: String, name: String, type: PlaceType, city: String?, priority: Priority?) {
        self.id = id
        self.name = name
        self.type = type
        self.city = city
        self.priority = priority
    }
}

public struct NavigationPoint: Identifiable, Codable, Equatable, Sendable {
    public let id: String
    public let name: String
    public let address: String?
    public let type: NavigationPointType
    public let note: String?
    public let navigationKeyword: String?
    public let isRecommended: Bool
    public let verificationStatus: VerificationStatus
    /// Nil intentionally represents a point whose exact coordinate is still pending verification.
    public let primaryCoordinate: GeoCoordinate?
    public let alternateCoordinates: [GeoCoordinate]

    public init(
        id: String,
        name: String,
        address: String?,
        type: NavigationPointType,
        note: String?,
        navigationKeyword: String?,
        isRecommended: Bool,
        verificationStatus: VerificationStatus,
        primaryCoordinate: GeoCoordinate?,
        alternateCoordinates: [GeoCoordinate]
    ) {
        self.id = id
        self.name = name
        self.address = address
        self.type = type
        self.note = note
        self.navigationKeyword = navigationKeyword
        self.isRecommended = isRecommended
        self.verificationStatus = verificationStatus
        self.primaryCoordinate = primaryCoordinate
        self.alternateCoordinates = alternateCoordinates
    }
}

public struct TripStop: Identifiable, Codable, Equatable, Sendable {
    public let id: String
    public let sequence: Int
    public let type: StopType
    public let priority: Priority?
    public let isOptional: Bool
    public let status: StopStatus
    public let place: Place
    public let navigationPoints: [NavigationPoint]

    public init(
        id: String,
        sequence: Int,
        type: StopType,
        priority: Priority?,
        isOptional: Bool,
        status: StopStatus,
        place: Place,
        navigationPoints: [NavigationPoint]
    ) {
        self.id = id
        self.sequence = sequence
        self.type = type
        self.priority = priority
        self.isOptional = isOptional
        self.status = status
        self.place = place
        self.navigationPoints = navigationPoints
    }

    private enum CodingKeys: String, CodingKey {
        case id, sequence, type, priority, optional, status, place
        case recommendedNavigationPoint, alternativeNavigationPoints
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        sequence = try container.decode(Int.self, forKey: .sequence)
        type = try container.decode(StopType.self, forKey: .type)
        priority = try container.decodeIfPresent(Priority.self, forKey: .priority)
        isOptional = try container.decode(Bool.self, forKey: .optional)
        status = try container.decode(StopStatus.self, forKey: .status)
        place = try container.decode(Place.self, forKey: .place)
        let recommended = try container.decodeIfPresent(NavigationPoint.self, forKey: .recommendedNavigationPoint)
        let alternatives = try container.decodeIfPresent([NavigationPoint].self, forKey: .alternativeNavigationPoints) ?? []
        navigationPoints = (recommended.map { [$0] } ?? []) + alternatives
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(sequence, forKey: .sequence)
        try container.encode(type, forKey: .type)
        try container.encodeIfPresent(priority, forKey: .priority)
        try container.encode(isOptional, forKey: .optional)
        try container.encode(status, forKey: .status)
        try container.encode(place, forKey: .place)
        let recommended = navigationPoints.first(where: \.isRecommended)
        try container.encodeIfPresent(recommended, forKey: .recommendedNavigationPoint)
        try container.encode(navigationPoints.filter { !$0.isRecommended }, forKey: .alternativeNavigationPoints)
    }
}

public struct Stay: Codable, Equatable, Sendable {
    public let hotelName: String
    public let address: String?
    public let phone: String?
    public let verificationStatus: VerificationStatus

    public init(hotelName: String, address: String?, phone: String?, verificationStatus: VerificationStatus) {
        self.hotelName = hotelName
        self.address = address
        self.phone = phone
        self.verificationStatus = verificationStatus
    }
}

public struct TripDay: Identifiable, Codable, Equatable, Sendable {
    public let id: String
    public let number: Int
    public let date: Date
    public let title: String
    public let type: DayType
    public let plannedDistanceKm: Int?
    public let plannedDistance: String?
    public let plannedDrivingMinutes: Int?
    public let plannedDrivingDuration: String?
    public let origin: Place
    public let destination: Place
    public let stops: [TripStop]
    public let stay: Stay?

    private enum CodingKeys: String, CodingKey {
        case id, number, date, title, type, plannedDistanceKm, plannedDistance, plannedDrivingMinutes, plannedDrivingDuration, origin, destination, stops, stay
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        number = try container.decode(Int.self, forKey: .number)
        let dateString = try container.decode(String.self, forKey: .date)
        guard let parsedDate = TripDateCodec.day.date(from: dateString) else {
            throw DecodingError.dataCorruptedError(forKey: .date, in: container, debugDescription: "Invalid trip day date: \(dateString)")
        }
        date = parsedDate
        title = try container.decode(String.self, forKey: .title)
        type = try container.decode(DayType.self, forKey: .type)
        plannedDistanceKm = try container.decodeIfPresent(Int.self, forKey: .plannedDistanceKm)
        plannedDistance = try container.decodeIfPresent(String.self, forKey: .plannedDistance)
        plannedDrivingMinutes = try container.decodeIfPresent(Int.self, forKey: .plannedDrivingMinutes)
        plannedDrivingDuration = try container.decodeIfPresent(String.self, forKey: .plannedDrivingDuration)
        origin = try container.decode(Place.self, forKey: .origin)
        destination = try container.decode(Place.self, forKey: .destination)
        stops = try container.decodeIfPresent([TripStop].self, forKey: .stops) ?? []
        stay = try container.decodeIfPresent(Stay.self, forKey: .stay)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(number, forKey: .number)
        try container.encode(TripDateCodec.day.string(from: date), forKey: .date)
        try container.encode(title, forKey: .title)
        try container.encode(type, forKey: .type)
        try container.encodeIfPresent(plannedDistanceKm, forKey: .plannedDistanceKm)
        try container.encodeIfPresent(plannedDistance, forKey: .plannedDistance)
        try container.encodeIfPresent(plannedDrivingMinutes, forKey: .plannedDrivingMinutes)
        try container.encodeIfPresent(plannedDrivingDuration, forKey: .plannedDrivingDuration)
        try container.encode(origin, forKey: .origin)
        try container.encode(destination, forKey: .destination)
        try container.encode(stops, forKey: .stops)
        try container.encodeIfPresent(stay, forKey: .stay)
    }
}

public struct Trip: Identifiable, Codable, Equatable, Sendable {
    public let schemaVersion: String
    public let id: String
    public let name: String
    public let startDate: Date
    public let endDate: Date
    public let durationDays: Int
    public let revision: Int
    public let updatedAt: Date
    public let days: [TripDay]

    private enum CodingKeys: String, CodingKey {
        case schemaVersion, tripId, tripName, startDate, endDate, durationDays, revision, updatedAt, days
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        schemaVersion = try container.decode(String.self, forKey: .schemaVersion)
        id = try container.decode(String.self, forKey: .tripId)
        name = try container.decode(String.self, forKey: .tripName)
        let start = try container.decode(String.self, forKey: .startDate)
        let end = try container.decode(String.self, forKey: .endDate)
        guard let parsedStart = TripDateCodec.day.date(from: start), let parsedEnd = TripDateCodec.day.date(from: end) else {
            throw DecodingError.dataCorruptedError(forKey: .startDate, in: container, debugDescription: "Invalid trip date")
        }
        startDate = parsedStart
        endDate = parsedEnd
        durationDays = try container.decode(Int.self, forKey: .durationDays)
        revision = try container.decode(Int.self, forKey: .revision)
        let updated = try container.decode(String.self, forKey: .updatedAt)
        guard let parsedUpdated = TripDateCodec.timestampDate(from: updated) else {
            throw DecodingError.dataCorruptedError(forKey: .updatedAt, in: container, debugDescription: "Invalid updatedAt: \(updated)")
        }
        updatedAt = parsedUpdated
        days = try container.decode([TripDay].self, forKey: .days)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(schemaVersion, forKey: .schemaVersion)
        try container.encode(id, forKey: .tripId)
        try container.encode(name, forKey: .tripName)
        try container.encode(TripDateCodec.day.string(from: startDate), forKey: .startDate)
        try container.encode(TripDateCodec.day.string(from: endDate), forKey: .endDate)
        try container.encode(durationDays, forKey: .durationDays)
        try container.encode(revision, forKey: .revision)
        try container.encode(TripDateCodec.timestampString(from: updatedAt), forKey: .updatedAt)
        try container.encode(days, forKey: .days)
    }
}
