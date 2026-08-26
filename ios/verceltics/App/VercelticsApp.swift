import SwiftUI
import UIKit

@main
struct VercelticsApp: App {
    @State private var authManager: AuthManager
    @State private var paywallManager = PaywallManager()
    @State private var appUpdateChecker = AppUpdateChecker()
    @State private var appearanceStore = AppAppearanceStore()
    @State private var registrarStore: RegistrarStore
    @State private var siteStore: SiteStore
    @State private var firstLaunchExperience = FirstLaunchExperienceStore()

    init() {
#if DEBUG
        if AppDebugFixtures.usesIsolatedAccountStores {
            _authManager = State(initialValue: AuthManager(ephemeralAccounts: []))
            _registrarStore = State(initialValue: RegistrarStore(ephemeralAccounts: []))
            _siteStore = State(initialValue: AppDebugFixtures.makeSiteStore())
        } else {
            _authManager = State(initialValue: AuthManager())
            _registrarStore = State(initialValue: RegistrarStore())
            _siteStore = State(initialValue: SiteStore())
        }
#else
        _authManager = State(initialValue: AuthManager())
        _registrarStore = State(initialValue: RegistrarStore())
        _siteStore = State(initialValue: SiteStore())
#endif

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
#if DEBUG
                if AppDebugFixtures.showsRegistrarDomain {
                    AppDebugFixtures.registrarDomainView
                } else if AppDebugFixtures.showsMainNavigation {
                    AppDebugFixtures.mainNavigationView
                } else {
                    appContent
                }
#else
                appContent
#endif
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
#if DEBUG
                guard !AppDebugFixtures.usesIsolatedAccountStores else { return }
#endif
                guard paywallManager.hasCheckedEntitlements else { return }
                firstLaunchExperience.migrateIfNeeded(
                    hasAnyConnection: hasAnyConnection,
                    hasActiveSubscription: paywallManager.hasActiveSubscription
                )
            }
        }
    }

    @ViewBuilder
    private var appContent: some View {
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
}
