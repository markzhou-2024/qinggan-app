import Foundation

public enum FamilyRole: String, CaseIterable, NormalizedStringCodable, Sendable {
    case father
    case mother
    case olderSister = "older_sister"
    case youngerBrother = "younger_brother"
    case grandfather
    case grandmother

    public var label: String {
        switch self {
        case .father: "爸爸"
        case .mother: "妈妈"
        case .olderSister: "姐姐"
        case .youngerBrother: "弟弟"
        case .grandfather: "爷爷"
        case .grandmother: "奶奶"
        }
    }
}

public enum FamilyRoleBindingStatus: String, NormalizedStringCodable, Sendable {
    case available
    case bound
}

public struct FamilyRoleOption: Codable, Equatable, Sendable {
    public let role: FamilyRole
    public let label: String
    public let bindingStatus: FamilyRoleBindingStatus
    public let boundDeviceDisplayName: String?
    public let boundAt: Date?
    public let bindingVersion: Int64

    public init(role: FamilyRole, label: String, bindingStatus: FamilyRoleBindingStatus,
                boundDeviceDisplayName: String?, boundAt: Date?, bindingVersion: Int64) {
        self.role = role
        self.label = label
        self.bindingStatus = bindingStatus
        self.boundDeviceDisplayName = boundDeviceDisplayName
        self.boundAt = boundAt
        self.bindingVersion = bindingVersion
    }

    private enum CodingKeys: String, CodingKey { case role, label, bindingStatus, boundDeviceDisplayName, boundAt, bindingVersion }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        role = try container.decode(FamilyRole.self, forKey: .role)
        label = try container.decode(String.self, forKey: .label)
        bindingStatus = try container.decode(FamilyRoleBindingStatus.self, forKey: .bindingStatus)
        boundDeviceDisplayName = try container.decodeIfPresent(String.self, forKey: .boundDeviceDisplayName)
        boundAt = try container.decodeTimestampIfPresent(forKey: .boundAt)
        bindingVersion = try container.decode(Int64.self, forKey: .bindingVersion)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(role, forKey: .role)
        try container.encode(label, forKey: .label)
        try container.encode(bindingStatus, forKey: .bindingStatus)
        try container.encodeIfPresent(boundDeviceDisplayName, forKey: .boundDeviceDisplayName)
        try container.encodeTimestampIfPresent(boundAt, forKey: .boundAt)
        try container.encode(bindingVersion, forKey: .bindingVersion)
    }
}

public struct FamilyRolesSnapshot: Codable, Equatable, Sendable {
    public let tripID: String
    public let roles: [FamilyRoleOption]

    public init(tripID: String, roles: [FamilyRoleOption]) {
        self.tripID = tripID
        self.roles = roles
    }

    private enum CodingKeys: String, CodingKey { case tripID = "tripId", roles }
}

public enum DeviceBindingStatus: String, NormalizedStringCodable, Sendable {
    case active
    case revoked
}

public struct DeviceBindingSnapshot: Codable, Equatable, Sendable {
    public let tripID: String
    public let role: FamilyRole
    public let deviceID: String
    public let deviceName: String
    public let bindingVersion: Int64
    public let status: DeviceBindingStatus

    public init(tripID: String, role: FamilyRole, deviceID: String, deviceName: String,
                bindingVersion: Int64, status: DeviceBindingStatus) {
        self.tripID = tripID
        self.role = role
        self.deviceID = deviceID
        self.deviceName = deviceName
        self.bindingVersion = bindingVersion
        self.status = status
    }

    private enum CodingKeys: String, CodingKey { case tripID = "tripId", role, deviceID = "deviceId", deviceName, bindingVersion, status }
}

private extension KeyedDecodingContainer {
    func decodeTimestampIfPresent(forKey key: Key) throws -> Date? {
        guard let value = try decodeIfPresent(String.self, forKey: key) else { return nil }
        guard let date = TripDateCodec.timestampDate(from: value) else {
            throw DecodingError.dataCorruptedError(forKey: key, in: self, debugDescription: "Invalid timestamp")
        }
        return date
    }
}

private extension KeyedEncodingContainer {
    mutating func encodeTimestampIfPresent(_ value: Date?, forKey key: Key) throws {
        try encodeIfPresent(value.map(TripDateCodec.timestampString(from:)), forKey: key)
    }
}
