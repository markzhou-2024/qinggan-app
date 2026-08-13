import Foundation
import XCTest
@testable import QingGanCore

final class FamilyModelsTests: XCTestCase {
    func testFamilyRoleDecodesServerCodesAndHasStableChineseLabels() throws {
        let decoder = JSONDecoder()
        let roles: [(String, FamilyRole, String)] = [
            ("FATHER", .father, "爸爸"),
            ("MOTHER", .mother, "妈妈"),
            ("OLDER_SISTER", .olderSister, "姐姐"),
            ("YOUNGER_BROTHER", .youngerBrother, "弟弟"),
            ("GRANDFATHER", .grandfather, "爷爷"),
            ("GRANDMOTHER", .grandmother, "奶奶")
        ]

        for (serverCode, role, label) in roles {
            XCTAssertEqual(try decoder.decode(FamilyRole.self, from: Data("\"\(serverCode)\"".utf8)), role)
            XCTAssertEqual(role.label, label)
        }
    }

    func testFamilyRoleEncodesServerUppercaseCodes() throws {
        for role in FamilyRole.allCases {
            let data = try JSONEncoder().encode(role)
            XCTAssertEqual(
                try JSONDecoder().decode(FamilyRole.self, from: data),
                role
            )
            XCTAssertEqual(String(decoding: data, as: UTF8.self), "\"\(role.rawValue.uppercased())\"")
        }
        let status = try JSONDecoder().decode(FamilyRoleBindingStatus.self, from: Data("\"BOUND\"".utf8))
        XCTAssertEqual(status, .bound)
    }

    func testFamilyRolesSnapshotDecodesTask2Shape() throws {
        let json = """
        {"tripId":"qinggan-2026-family","roles":[
          {"role":"FATHER","label":"爸爸","bindingStatus":"BOUND","boundDeviceDisplayName":"爸爸的 iPhone","boundAt":"2026-08-12T03:26:04Z","bindingVersion":2},
          {"role":"MOTHER","label":"妈妈","bindingStatus":"AVAILABLE","boundDeviceDisplayName":null,"boundAt":null,"bindingVersion":0}
        ]}
        """
        let snapshot = try JSONDecoder().decode(FamilyRolesSnapshot.self, from: Data(json.utf8))
        XCTAssertEqual(snapshot.tripID, "qinggan-2026-family")
        XCTAssertEqual(snapshot.roles[0].role, .father)
        XCTAssertEqual(snapshot.roles[0].bindingVersion, 2)
        XCTAssertEqual(snapshot.roles[1].bindingStatus, .available)
    }
}
