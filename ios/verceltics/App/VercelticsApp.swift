import SwiftUI
import UIKit

@main
struct VercelticsApp: App {
    @State private var authManager = AuthManager()
    @State private var paywallManager = PaywallManager()
    @State private var appUpdateChecker = AppUpdateChecker()
    @State private var appearanceStore = AppAppearanceStore()
    @State private var registrarStore = RegistrarStore()
    @State private var siteStore = SiteStore()
    @State private var firstLaunchExperience = FirstLaunchExperienceStore()

    init() {
        let segmentedControl = UISegmentedControl.appearance()
        segmentedControl.selectedSegmentTintColor = UIColor(AppTheme.signalFill)
        segmentedControl.setTitleTextAttributes(
            [
                .foregroundColor: UIColor(AppTheme.signalForeground),
                .font: UIFont.systemFont(ofSize: 13, weight: .bold),
            ],
            for: .selected
        )
        segmentedControl.setTitleTextAttributes(
            [
                .foregroundColor: UIColor(AppTheme.textPrimary),
                .font: UIFont.systemFont(ofSize: 13, weight: .semibold),
            ],
            for: .normal
        )
    }

    private var hasAnyConnection: Bool {
        !authManager.accounts.isEmpty
            || !registrarStore.accounts.isEmpty
            || !siteStore.accounts.isEmpty
    }

    private var firstLaunchMigrationState: Int {
        (paywallManager.hasCheckedEntitlements ? 1 : 0)
            | (hasAnyConnection ? 2 : 0)
            | (paywallManager.hasActiveSubscription ? 4 : 0)
    }

    var body: some Scene {
        WindowGroup {
            Group {
                if !paywallManager.hasCheckedEntitlements {
                    ZStack {
                        AppTheme.canvas.ignoresSafeArea()
                        VStack(spacing: 14) {
                            Image("AppLogo")
                                .resizable()
                                .scaledToFit()
                                .frame(width: 64, height: 64)
                                .accessibilityHidden(true)
                            ProgressView()
                                .tint(AppTheme.textSecondary)
                            Text("Loading workspace")
                                .font(.footnote)
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                        .accessibilityElement(children: .combine)
                    }
                } else if !hasAnyConnection {
                    FirstConnectionFlow(
                        experience: firstLaunchExperience,
                        hasAnyConnection: hasAnyConnection,
                        hasActiveSubscription: paywallManager.hasActiveSubscription
                    )
                } else {
                    // Soft paywall: connection and workspace browsing stay
                    // available; item details and provider actions gate inside
                    // their owning views.
                    MainTabView()
                }
            }
            .environment(authManager)
            .environment(paywallManager)
            .environment(appUpdateChecker)
            .environment(appearanceStore)
            .environment(registrarStore)
            .environment(siteStore)
            .appNativeControlTheme()
            .preferredColorScheme(appearanceStore.selection.preferredColorScheme)
            .task(id: firstLaunchMigrationState) {
                guard paywallManager.hasCheckedEntitlements else { return }
                firstLaunchExperience.migrateIfNeeded(
                    hasAnyConnection: hasAnyConnection,
                    hasActiveSubscription: paywallManager.hasActiveSubscription
                )
            }
        }
    }
}
