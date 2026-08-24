import XCTest
@testable import verceltics

final class MainTabPersistenceTests: XCTestCase {
    func testEveryPrimaryWorkspaceRestoresItsOwnTab() {
        for workspace in PrimaryWorkspace.allCases {
            XCTAssertEqual(
                PrimaryWorkspace.restored(from: workspace.rawValue),
                workspace
            )
            XCTAssertEqual(workspace.destination.primaryWorkspace, workspace)
        }
    }

    func testMissingOrInvalidWorkspaceFallsBackToHosting() {
        XCTAssertEqual(PrimaryWorkspace.restored(from: nil), .hosting)
        XCTAssertEqual(PrimaryWorkspace.restored(from: "support"), .hosting)
    }

    func testSecondaryTabsDoNotReplaceLastPrimaryWorkspace() {
        XCTAssertNil(MainTabDestination.search.primaryWorkspace)
        XCTAssertNil(MainTabDestination.about.primaryWorkspace)
    }

    func testSearchKeepsThePreferredConnectedWorkspace() {
        XCTAssertEqual(
            PrimaryWorkspace.searchTarget(
                preferred: .registrars,
                connectedWorkspaces: [.hosting, .registrars, .sites]
            ),
            .registrars
        )
    }

    func testSearchFallsBackToAnAvailableConnectedWorkspace() {
        XCTAssertEqual(
            PrimaryWorkspace.searchTarget(
                preferred: .hosting,
                connectedWorkspaces: [.sites]
            ),
            .sites
        )
    }

    func testSearchKeepsThePreferredWorkspaceWhenNothingIsConnected() {
        XCTAssertEqual(
            PrimaryWorkspace.searchTarget(
                preferred: .sites,
                connectedWorkspaces: []
            ),
            .sites
        )
    }
}
