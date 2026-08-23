#if DEBUG
import SwiftUI

enum SharedUIDebugFixtures {
    static let registrarDomainArgument = "-sharedUIRegistrarDomainFixture"

    static var showsRegistrarDomain: Bool {
        ProcessInfo.processInfo.arguments.contains(registrarDomainArgument)
    }

    static var registrarDomainView: some View {
        let account = RegistrarAccount(
            provider: .namecheap,
            name: "Fixture registrar",
            primaryCredential: "fixture-only"
        )
        let domain = RegistrarDomain(
            name: "example.dev",
            status: "active",
            createdAt: Calendar.current.date(byAdding: .year, value: -2, to: .now),
            expiresAt: Calendar.current.date(byAdding: .day, value: 23, to: .now),
            autoRenew: true,
            locked: true,
            privacyEnabled: true,
            nameservers: ["dns1.registrar-servers.com", "dns2.registrar-servers.com"],
            metadata: [:]
        )

        return NavigationStack {
            RegistrarDomainDetailView(
                account: account,
                domain: domain,
                usesSharedUI: true
            )
        }
    }
}
#endif
