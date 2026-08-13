import Foundation

public struct StopExecution: Codable, Equatable, Sendable {
    public let stopID: String
    public let status: StopStatus
    public let updatedAt: Date
    public let updatedByRole: FamilyRole
    public let updatedByDeviceID: String

    public init(stopID: String, status: StopStatus, updatedAt: Date, updatedByRole: FamilyRole, updatedByDeviceID: String) {
        self.stopID = stopID
        self.status = status
        self.updatedAt = updatedAt
        self.updatedByRole = updatedByRole
        self.updatedByDeviceID = updatedByDeviceID
    }

    private enum CodingKeys: String, CodingKey { case stopID = "stopId", status, updatedAt, updatedByRole, updatedByDeviceID = "updatedByDeviceId" }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        stopID = try container.decode(String.self, forKey: .stopID)
        status = try container.decode(StopStatus.self, forKey: .status)
        updatedAt = try container.decodeTimestamp(forKey: .updatedAt)
        updatedByRole = try container.decode(FamilyRole.self, forKey: .updatedByRole)
        updatedByDeviceID = try container.decode(String.self, forKey: .updatedByDeviceID)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(stopID, forKey: .stopID)
        try container.encode(status, forKey: .status)
        try container.encode(TripDateCodec.timestampString(from: updatedAt), forKey: .updatedAt)
        try container.encode(updatedByRole, forKey: .updatedByRole)
        try container.encode(updatedByDeviceID, forKey: .updatedByDeviceID)
    }
}

public struct TripExecutionSnapshot: Codable, Equatable, Sendable {
    public let schemaVersion: String
    public let tripID: String
    public let revision: Int64
    public let status: TripLifecycleStatus
    public let actualStartDate: Date?
    public let updatedAt: Date
    public let stopStates: [StopExecution]

    public init(schemaVersion: String, tripID: String, revision: Int64, status: TripLifecycleStatus,
                actualStartDate: Date?, updatedAt: Date, stopStates: [StopExecution]) {
        self.schemaVersion = schemaVersion
        self.tripID = tripID
        self.revision = revision
        self.status = status
        self.actualStartDate = actualStartDate
        self.updatedAt = updatedAt
        self.stopStates = stopStates
    }

    private enum CodingKeys: String, CodingKey { case schemaVersion, tripID = "tripId", revision, status, actualStartDate, updatedAt, stopStates }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        schemaVersion = try container.decode(String.self, forKey: .schemaVersion)
        tripID = try container.decode(String.self, forKey: .tripID)
        revision = try container.decode(Int64.self, forKey: .revision)
        status = try container.decode(TripLifecycleStatus.self, forKey: .status)
        actualStartDate = try container.decodeDayIfPresent(forKey: .actualStartDate)
        updatedAt = try container.decodeTimestamp(forKey: .updatedAt)
        stopStates = try container.decode([StopExecution].self, forKey: .stopStates)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(schemaVersion, forKey: .schemaVersion)
        try container.encode(tripID, forKey: .tripID)
        try container.encode(revision, forKey: .revision)
        try container.encode(status, forKey: .status)
        try container.encodeIfPresent(actualStartDate.map(TripDateCodec.day.string(from:)), forKey: .actualStartDate)
        try container.encode(TripDateCodec.timestampString(from: updatedAt), forKey: .updatedAt)
        try container.encode(stopStates, forKey: .stopStates)
    }
}

public enum ExecutionAction: String, NormalizedStringCodable, Sendable {
    case start
    case arrive
    case complete
    case skip
}

public struct PendingExecutionAction: Codable, Equatable, Identifiable, Sendable {
    public let id: UUID
    public let tripID: String
    public let stopID: String?
    public let action: ExecutionAction
    public let occurredAt: Date
    public let createdAt: Date
    public let deviceID: String
    public let bindingVersion: Int64

    public init(id: UUID, tripID: String, stopID: String?, action: ExecutionAction, occurredAt: Date,
                createdAt: Date, deviceID: String, bindingVersion: Int64) {
        self.id = id
        self.tripID = tripID
        self.stopID = stopID
        self.action = action
        self.occurredAt = occurredAt
        self.createdAt = createdAt
        self.deviceID = deviceID
        self.bindingVersion = bindingVersion
    }

    private enum CodingKeys: String, CodingKey { case id, tripID = "tripId", stopID = "stopId", action, occurredAt, createdAt, deviceID = "deviceId", bindingVersion }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        tripID = try container.decode(String.self, forKey: .tripID)
        stopID = try container.decodeIfPresent(String.self, forKey: .stopID)
        action = try container.decode(ExecutionAction.self, forKey: .action)
        occurredAt = try container.decodeTimestamp(forKey: .occurredAt)
        createdAt = try container.decodeTimestamp(forKey: .createdAt)
        deviceID = try container.decode(String.self, forKey: .deviceID)
        bindingVersion = try container.decode(Int64.self, forKey: .bindingVersion)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(tripID, forKey: .tripID)
        try container.encodeIfPresent(stopID, forKey: .stopID)
        try container.encode(action, forKey: .action)
        try container.encode(TripDateCodec.timestampString(from: occurredAt), forKey: .occurredAt)
        try container.encode(TripDateCodec.timestampString(from: createdAt), forKey: .createdAt)
        try container.encode(deviceID, forKey: .deviceID)
        try container.encode(bindingVersion, forKey: .bindingVersion)
    }
}

public enum PendingActionResolution: Equatable, Sendable {
    case retryable
    case resolvedNoOp
    case invalid
}

private extension KeyedDecodingContainer {
    func decodeTimestamp(forKey key: Key) throws -> Date {
        let value = try decode(String.self, forKey: key)
        guard let date = TripDateCodec.timestampDate(from: value) else {
            throw DecodingError.dataCorruptedError(forKey: key, in: self, debugDescription: "Invalid timestamp")
        }
        return date
    }

    func decodeDayIfPresent(forKey key: Key) throws -> Date? {
        guard let value = try decodeIfPresent(String.self, forKey: key) else { return nil }
        guard let date = TripDateCodec.day.date(from: value) else {
            throw DecodingError.dataCorruptedError(forKey: key, in: self, debugDescription: "Invalid date-only value")
        }
        return date
    }
}
