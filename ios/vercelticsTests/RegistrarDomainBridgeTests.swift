import XCTest
@testable import verceltics

final class RegistrarDomainBridgeTests: XCTestCase {
    func testSnapshotContainsDisplayDataButNeverRegistrarCredentials() throws {
        let accountID = UUID()
        let secret = "registrar-secret-that-must-stay-native"
        let account = RegistrarAccount(
            id: accountID,
            provider: .namecheap,
            name: "Production registrar",
            primaryCredential: secret,
            secondaryCredential: "secondary-secret",
            metadata: ["apiUser": "private-user"]
        )
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try XCTUnwrap(TimeZone(secondsFromGMT: 0))
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let expiresAt = try XCTUnwrap(
            calendar.date(byAdding: .day, value: 10, to: now)
        )
        let domain = RegistrarDomain(
            name: "example.dev",
            status: "active",
            createdAt: calendar.date(byAdding: .year, value: -2, to: now),
            expiresAt: expiresAt,
            autoRenew: true,
            locked: false,
            privacyEnabled: nil,
            nameservers: ["dns1.example.dev", "dns2.example.dev"],
            metadata: ["providerPayload": secret]
        )

        let snapshot = RegistrarDomainBridgeMapper.snapshot(
            account: account,
            domain: domain,
            now: now,
            calendar: calendar
        )

        XCTAssertEqual(snapshot.schemaVersion, 1)
        XCTAssertEqual(snapshot.accountId, accountID.uuidString)
        XCTAssertEqual(snapshot.providerId, RegistrarProvider.namecheap.rawValue)
        XCTAssertEqual(snapshot.domainName, "example.dev")
        XCTAssertEqual(snapshot.statusTone, .success)
        XCTAssertEqual(snapshot.expiryTone, .warning)
        XCTAssertEqual(snapshot.expiryValue, "10")
        XCTAssertEqual(snapshot.autoRenewLabel, "On")
        XCTAssertEqual(snapshot.transferLockLabel, "Off")
        XCTAssertEqual(snapshot.privacyLabel, "Not returned")
        XCTAssertEqual(snapshot.nameservers, domain.nameservers)

        let visiblePayload = [
            snapshot.accountId,
            snapshot.providerId,
            snapshot.providerName,
            snapshot.domainName,
            snapshot.statusLabel,
            snapshot.expiryValue,
            snapshot.expiryLabel,
            snapshot.autoRenewLabel,
            snapshot.transferLockLabel,
            snapshot.privacyLabel,
            snapshot.nameservers.joined(separator: "|"),
        ].joined(separator: "|")
        XCTAssertFalse(visiblePayload.contains(secret))
        XCTAssertFalse(visiblePayload.contains("secondary-secret"))
        XCTAssertFalse(visiblePayload.contains("private-user"))
    }

    func testRegistrarDomainFlutterFlagDefaultsOnAndCanRollBack() throws {
        let suiteName = "RegistrarDomainBridgeTests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }

        XCTAssertTrue(
            SharedUIFeatureFlags.registrarDomainDetailEnabled(defaults: defaults)
        )

        SharedUIFeatureFlags.setRegistrarDomainDetailEnabled(
            false,
            defaults: defaults
        )
        XCTAssertFalse(
            SharedUIFeatureFlags.registrarDomainDetailEnabled(defaults: defaults)
        )

        SharedUIFeatureFlags.setRegistrarDomainDetailEnabled(
            true,
            defaults: defaults
        )
        XCTAssertTrue(
            SharedUIFeatureFlags.registrarDomainDetailEnabled(defaults: defaults)
        )
    }
}
