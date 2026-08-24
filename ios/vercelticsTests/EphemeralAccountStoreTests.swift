import XCTest
@testable import verceltics

@MainActor
final class EphemeralAccountStoreTests: XCTestCase {
    func testAuthFixtureStoreMutatesOnlyItsInMemoryAccounts() {
        let first = VercelAccount(name: "First", token: "fixture-one")
        let second = VercelAccount(name: "Second", token: "fixture-two")
        let store = AuthManager(
            ephemeralAccounts: [first, second],
            activeAccountID: first.id
        )

        store.switchAccount(to: second.id)
        XCTAssertEqual(store.activeAccountId, second.id)

        store.removeAccount(id: second.id)
        XCTAssertEqual(store.accounts.map(\.id), [first.id])
        XCTAssertEqual(store.activeAccountId, first.id)
    }

    func testRegistrarFixtureStoreMutatesOnlyItsInMemoryAccounts() {
        let first = RegistrarAccount(
            provider: .namecheap,
            name: "First",
            primaryCredential: "fixture-one"
        )
        let second = RegistrarAccount(
            provider: .porkbun,
            name: "Second",
            primaryCredential: "fixture-two"
        )
        let store = RegistrarStore(
            ephemeralAccounts: [first, second],
            activeAccountID: first.id
        )

        store.switchAccount(to: second.id)
        XCTAssertEqual(store.activeAccountID, second.id)

        store.removeAll()
        XCTAssertTrue(store.accounts.isEmpty)
        XCTAssertNil(store.activeAccountID)
    }

    func testSiteFixtureStoreKeepsSeededSnapshotInMemory() {
        let account = SiteIntegrationAccount(
            provider: .pageSpeed,
            name: "Fixture",
            credential: "fixture-only"
        )
        let snapshot = SiteIntegrationSnapshot(
            accountID: account.id,
            provider: account.provider,
            status: "Healthy"
        )
        let store = SiteStore(
            ephemeralAccounts: [account],
            activeAccountID: account.id,
            snapshots: [account.id: snapshot]
        )

        XCTAssertEqual(store.activeAccountID, account.id)
        XCTAssertEqual(store.snapshot()?.status, "Healthy")

        store.removeAll()
        XCTAssertTrue(store.accounts.isEmpty)
        XCTAssertTrue(store.snapshots.isEmpty)
        XCTAssertNil(store.activeAccountID)
    }
}
