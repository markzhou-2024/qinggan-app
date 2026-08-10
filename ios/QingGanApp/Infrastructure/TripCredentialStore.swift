import Foundation
import Security

protocol TripCredentialStore {
    func readToken() throws -> String?
    func save(token: String) throws
}

/// The token is deliberately supplied only at install/configuration time and is never embedded in source or fixtures.
struct KeychainTripCredentialStore: TripCredentialStore {
    private let service = "com.qinggan.family"
    private let account = "family-trip-token"

    func readToken() throws -> String? {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account, kSecReturnData as String: true]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = item as? Data else { throw KeychainError.unavailable(status) }
        return String(data: data, encoding: .utf8)
    }

    func save(token: String) throws {
        let value = Data(token.utf8)
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account]
        let attributes: [String: Any] = [kSecValueData as String: value]
        let update = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if update == errSecItemNotFound {
            var insert = query
            insert[kSecValueData as String] = value
            let status = SecItemAdd(insert as CFDictionary, nil)
            guard status == errSecSuccess else { throw KeychainError.unavailable(status) }
        } else if update != errSecSuccess { throw KeychainError.unavailable(update) }
    }
}

enum KeychainError: Error { case unavailable(OSStatus) }
