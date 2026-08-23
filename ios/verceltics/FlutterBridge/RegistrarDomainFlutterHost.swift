import Flutter
import FlutterPluginRegistrant
import SwiftUI
import UIKit

enum SharedUIFeatureFlags {
    static let registrarDomainDetailKey = "sharedUI.registrarDomainDetail.enabled.v1"

    static func registrarDomainDetailEnabled(defaults: UserDefaults = .standard) -> Bool {
        guard defaults.object(forKey: registrarDomainDetailKey) != nil else {
            return true
        }
        return defaults.bool(forKey: registrarDomainDetailKey)
    }

    static func setRegistrarDomainDetailEnabled(
        _ isEnabled: Bool,
        defaults: UserDefaults = .standard
    ) {
        defaults.set(isEnabled, forKey: registrarDomainDetailKey)
    }
}

enum RegistrarDomainBridgeMapper {
    static let schemaVersion: Int64 = 1

    static func snapshot(
        account: RegistrarAccount,
        domain: RegistrarDomain,
        now: Date = .now,
        calendar: Calendar = .current
    ) -> RegistrarDomainSnapshot {
        let daysUntilExpiry = domain.expiresAt.map {
            calendar.dateComponents([.day], from: now, to: $0).day
        } ?? nil

        return RegistrarDomainSnapshot(
            schemaVersion: schemaVersion,
            accountId: account.id.uuidString,
            providerId: account.provider.rawValue,
            providerName: account.provider.displayName,
            domainName: domain.name,
            statusLabel: domain.status?.uppercased() ?? account.provider.displayName.uppercased(),
            statusTone: bridgeTone(AppStatusTone.status(domain.status ?? "")),
            expiryTone: expiryTone(daysUntilExpiry),
            expiryValue: daysUntilExpiry.map { abs($0).formatted() } ?? "—",
            expiryLabel: expiryLabel(daysUntilExpiry),
            expiryDateLabel: domain.expiresAt?.formatted(date: .abbreviated, time: .omitted),
            autoRenewLabel: booleanLabel(domain.autoRenew),
            transferLockLabel: booleanLabel(domain.locked),
            privacyLabel: booleanLabel(domain.privacyEnabled),
            registeredDateLabel: domain.createdAt?.formatted(date: .abbreviated, time: .omitted),
            nameservers: domain.nameservers,
            canOpenDomain: URL(string: "https://\(domain.name)") != nil,
            canOpenRegistrar: account.provider.dashboardURL != nil
        )
    }

    private static func booleanLabel(_ value: Bool?) -> String {
        switch value {
        case true: "On"
        case false: "Off"
        case nil: "Not returned"
        }
    }

    private static func expiryLabel(_ daysUntilExpiry: Int?) -> String {
        guard let daysUntilExpiry else { return "expiry unavailable" }
        return daysUntilExpiry < 0 ? "days expired" : "days left"
    }

    private static func expiryTone(_ daysUntilExpiry: Int?) -> RegistrarDomainStatusTone {
        guard let daysUntilExpiry else { return .neutral }
        if daysUntilExpiry < 0 { return .danger }
        if daysUntilExpiry <= 30 { return .warning }
        return .success
    }

    private static func bridgeTone(_ tone: AppStatusTone) -> RegistrarDomainStatusTone {
        switch tone {
        case .success: .success
        case .warning: .warning
        case .danger: .danger
        case .progress: .progress
        case .neutral: .neutral
        }
    }
}

@MainActor
private final class VercelticsFlutterEnginePool {
    static let shared = VercelticsFlutterEnginePool()

    private let group = FlutterEngineGroup(
        name: "com.apoorvdarshan.verceltics.shared-ui",
        project: nil
    )

    private init() {}

    func makeEngine(initialRoute: String) -> FlutterEngine {
        group.makeEngine(
            withEntrypoint: nil,
            libraryURI: nil,
            initialRoute: initialRoute
        )
    }
}

struct FlutterRegistrarDomainBody: UIViewControllerRepresentable {
    let snapshot: RegistrarDomainSnapshot
    let actionHandler: (RegistrarDomainAction) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(snapshot: snapshot, actionHandler: actionHandler)
    }

    func makeUIViewController(context: Context) -> FlutterViewController {
        context.coordinator.makeViewController()
    }

    func updateUIViewController(
        _ uiViewController: FlutterViewController,
        context: Context
    ) {
        context.coordinator.update(
            snapshot: snapshot,
            actionHandler: actionHandler
        )
    }

    static func dismantleUIViewController(
        _ uiViewController: FlutterViewController,
        coordinator: Coordinator
    ) {
        coordinator.shutDown()
    }

    @MainActor
    final class Coordinator {
        private let bridge: RegistrarDomainBridgeHandler
        private var engine: FlutterEngine?
        private var flutterAPI: RegistrarDomainFlutterApi?

        init(
            snapshot: RegistrarDomainSnapshot,
            actionHandler: @escaping (RegistrarDomainAction) -> Void
        ) {
            bridge = RegistrarDomainBridgeHandler(
                snapshot: snapshot,
                actionHandler: actionHandler
            )
        }

        func makeViewController() -> FlutterViewController {
            let engine = VercelticsFlutterEnginePool.shared.makeEngine(
                initialRoute: "/registrar-domain"
            )
            GeneratedPluginRegistrant.register(with: engine)
            RegistrarDomainHostApiSetup.setUp(
                binaryMessenger: engine.binaryMessenger,
                api: bridge
            )
            flutterAPI = RegistrarDomainFlutterApi(
                binaryMessenger: engine.binaryMessenger
            )

            self.engine = engine
            let controller = FlutterViewController(
                engine: engine,
                nibName: nil,
                bundle: nil
            )
            controller.view.backgroundColor = UIColor(AppTheme.canvas)
            controller.view.isOpaque = true
            return controller
        }

        func update(
            snapshot: RegistrarDomainSnapshot,
            actionHandler: @escaping (RegistrarDomainAction) -> Void
        ) {
            let didChange = bridge.snapshot != snapshot
            bridge.snapshot = snapshot
            bridge.actionHandler = actionHandler
            if didChange {
                flutterAPI?.registrarDomainDidChange { _ in }
            }
        }

        func shutDown() {
            guard let engine else { return }
            RegistrarDomainHostApiSetup.setUp(
                binaryMessenger: engine.binaryMessenger,
                api: nil
            )
            flutterAPI = nil
            engine.destroyContext()
            self.engine = nil
        }
    }
}

@MainActor
private final class RegistrarDomainBridgeHandler: RegistrarDomainHostApi {
    var snapshot: RegistrarDomainSnapshot
    var actionHandler: (RegistrarDomainAction) -> Void

    init(
        snapshot: RegistrarDomainSnapshot,
        actionHandler: @escaping (RegistrarDomainAction) -> Void
    ) {
        self.snapshot = snapshot
        self.actionHandler = actionHandler
    }

    func getRegistrarDomain() throws -> RegistrarDomainSnapshot {
        snapshot
    }

    func performRegistrarDomainAction(action: RegistrarDomainAction) throws {
        actionHandler(action)
    }
}
