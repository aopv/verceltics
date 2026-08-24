import SwiftUI

let lastPrimaryWorkspaceKey = "mainTab.lastPrimaryWorkspace"

enum PrimaryWorkspace: String, CaseIterable {
    case hosting
    case registrars
    case sites

    var destination: MainTabDestination {
        switch self {
        case .hosting: .hosting
        case .registrars: .registrars
        case .sites: .sites
        }
    }

    static func restored(from storedValue: String?) -> PrimaryWorkspace {
        storedValue.flatMap(PrimaryWorkspace.init(rawValue:)) ?? .hosting
    }

    static func searchTarget(
        preferred: PrimaryWorkspace,
        connectedWorkspaces: Set<PrimaryWorkspace>
    ) -> PrimaryWorkspace {
        if connectedWorkspaces.contains(preferred) {
            return preferred
        }
        return allCases.first(where: connectedWorkspaces.contains) ?? preferred
    }
}

enum MainTabDestination: Hashable {
    case hosting
    case search
    case registrars
    case sites
    case about

    var primaryWorkspace: PrimaryWorkspace? {
        switch self {
        case .hosting: .hosting
        case .registrars: .registrars
        case .sites: .sites
        case .search, .about: nil
        }
    }
}

struct MainTabView: View {
    @Environment(AppUpdateChecker.self) private var appUpdateChecker
    @Environment(AuthManager.self) private var authManager
    @Environment(RegistrarStore.self) private var registrarStore
    @Environment(SiteStore.self) private var siteStore
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage(lastPrimaryWorkspaceKey) private var lastPrimaryWorkspace = PrimaryWorkspace.hosting.rawValue
    @State private var selectedTab: MainTabDestination
    @State private var hostingSearchRequestID = 0
    @State private var registrarSearchRequestID = 0
    @State private var sitesSearchRequestID = 0
    @State private var hostingRefreshRequestID = 0
    @State private var registrarRefreshRequestID = 0
    @State private var sitesRefreshRequestID = 0
    private let performsUpdateCheck: Bool
    private let performsAutomaticRefresh: Bool

    init(
        performsUpdateCheck: Bool = true,
        performsAutomaticRefresh: Bool = true,
        initialWorkspace: PrimaryWorkspace? = nil
    ) {
        self.performsUpdateCheck = performsUpdateCheck
        self.performsAutomaticRefresh = performsAutomaticRefresh
        let storedValue = UserDefaults.standard.string(forKey: lastPrimaryWorkspaceKey)
        let workspace = initialWorkspace ?? PrimaryWorkspace.restored(from: storedValue)
        _selectedTab = State(initialValue: workspace.destination)
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            Tab(value: MainTabDestination.hosting) {
                providerHome(
                    searchRequestID: hostingSearchRequestID,
                    backgroundRefreshRequestID: hostingRefreshRequestID
                )
                    .id(activeHostingViewIdentity)
                    .appTabContent()
                    .accessibilityIdentifier("screen.hosting")
            } label: {
                Label("Hosting", systemImage: "server.rack")
            }

            Tab(value: MainTabDestination.registrars) {
                RegistrarsView(
                    searchRequestID: registrarSearchRequestID,
                    backgroundRefreshRequestID: registrarRefreshRequestID
                )
                .appTabContent()
                .accessibilityIdentifier("screen.registrars")
            } label: {
                Label("Registrars", systemImage: "globe.americas")
            }

            Tab(value: MainTabDestination.sites) {
                SitesView(
                    searchRequestID: sitesSearchRequestID,
                    backgroundRefreshRequestID: sitesRefreshRequestID,
                    performsAutomaticRefresh: performsAutomaticRefresh
                )
                .appTabContent()
                .accessibilityIdentifier("screen.sites")
            } label: {
                Label("Sites", systemImage: "chart.xyaxis.line")
            }

            Tab(value: MainTabDestination.about) {
                AboutView()
                    .appTabContent()
                    .accessibilityIdentifier("screen.about")
            } label: {
                Label("About", systemImage: "info.circle")
            }
            .badge(appUpdateChecker.isUpdateAvailable ? Text("") : nil)
        }
        .tabViewStyle(.sidebarAdaptable)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            AppGlassNavigationBar(
                selection: $selectedTab,
                showsAboutBadge: appUpdateChecker.isUpdateAvailable,
                searchAction: activateSearch
            )
            .padding(.horizontal, 12)
            .padding(.top, 8)
            .padding(.bottom, 4)
        }
        .onChange(of: selectedTab) { _, newValue in
            if newValue == .search {
                let workspace = PrimaryWorkspace.restored(from: lastPrimaryWorkspace)
                selectedTab = workspace.destination
                Task { @MainActor in
                    await Task.yield()
                    requestSearch(for: workspace)
                }
                return
            }
            if let workspace = newValue.primaryWorkspace {
                lastPrimaryWorkspace = workspace.rawValue
            }
        }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            requestBackgroundRefreshForCurrentWorkspace()
        }
        .task {
            guard performsUpdateCheck else { return }
            await appUpdateChecker.checkForUpdates()
        }
    }

    @ViewBuilder
    private func providerHome(
        searchRequestID: Int,
        backgroundRefreshRequestID: Int
    ) -> some View {
        if let credentials = authManager.cloudflareCredentials {
            CloudflareDashboardView(
                authenticationMode: credentials.mode,
                email: credentials.email,
                credential: credentials.credential,
                searchRequestID: searchRequestID,
                backgroundRefreshRequestID: backgroundRefreshRequestID
            )
        } else if let account = authManager.activeHostingAccount {
            HostingDashboardView(
                account: account,
                searchRequestID: searchRequestID,
                backgroundRefreshRequestID: backgroundRefreshRequestID
            )
        } else if authManager.activeProvider == .vercel {
            ProjectsView(
                searchRequestID: searchRequestID,
                backgroundRefreshRequestID: backgroundRefreshRequestID,
                initialToken: authManager.token
            )
        } else {
            HostingEmptyStateView()
        }
    }

    private func requestBackgroundRefreshForCurrentWorkspace() {
        guard scenePhase == .active else { return }
        let workspace = selectedTab.primaryWorkspace
            ?? PrimaryWorkspace.restored(from: lastPrimaryWorkspace)
        switch workspace {
        case .hosting:
            hostingRefreshRequestID &+= 1
        case .registrars:
            registrarRefreshRequestID &+= 1
        case .sites:
            sitesRefreshRequestID &+= 1
        }
    }

    private func requestSearch(for workspace: PrimaryWorkspace) {
        switch workspace {
        case .hosting:
            hostingSearchRequestID &+= 1
        case .registrars:
            registrarSearchRequestID &+= 1
        case .sites:
            sitesSearchRequestID &+= 1
        }
    }

    private func activateSearch() {
        let preferredWorkspace = selectedTab.primaryWorkspace
            ?? PrimaryWorkspace.restored(from: lastPrimaryWorkspace)
        let workspace = PrimaryWorkspace.searchTarget(
            preferred: preferredWorkspace,
            connectedWorkspaces: connectedSearchWorkspaces
        )
        if selectedTab != workspace.destination {
            selectedTab = workspace.destination
        }
        lastPrimaryWorkspace = workspace.rawValue
        Task { @MainActor in
            await Task.yield()
            requestSearch(for: workspace)
        }
    }

    private var connectedSearchWorkspaces: Set<PrimaryWorkspace> {
        var workspaces = Set<PrimaryWorkspace>()
        if authManager.activeAccount != nil {
            workspaces.insert(.hosting)
        }
        if registrarStore.activeAccount != nil {
            workspaces.insert(.registrars)
        }
        if siteStore.activeAccount != nil {
            workspaces.insert(.sites)
        }
        return workspaces
    }

    /// Rebuild provider-specific state when credentials are rotated in place.
    /// Account IDs intentionally remain stable during an update, so using the ID
    /// alone can leave an API client holding the previous credential.
    private var activeHostingViewIdentity: String {
        guard let account = authManager.activeAccount else { return "no-hosting-account" }
        let metadata = account.providerMetadata
            .sorted { $0.key < $1.key }
            .map { "\($0.key)=\($0.value)" }
            .joined(separator: "&")
        return "\(account.id.uuidString)|\(account.token.hashValue)|\(metadata.hashValue)"
    }
}

private struct AppGlassNavigationBar: View {
    @Binding var selection: MainTabDestination
    let showsAboutBadge: Bool
    let searchAction: () -> Void
    @ScaledMetric(relativeTo: .caption2) private var scaledDockHeight: CGFloat = 62
    @ScaledMetric(relativeTo: .caption2) private var scaledSearchWidth: CGFloat = 60
    @State private var searchHapticTrigger = 0
    @State private var selectionHapticTrigger = 0

    private let destinations: [MainTabDestination] = [
        .hosting,
        .registrars,
        .sites,
        .about,
    ]

    var body: some View {
        Group {
            if #available(iOS 26.0, *) {
                GlassEffectContainer(spacing: 10) {
                    navigationContent
                }
            } else {
                navigationContent
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Main navigation")
        .accessibilityIdentifier("mainNavigation.dock")
        .sensoryFeedback(.selection, trigger: selectionHapticTrigger)
    }

    private var navigationContent: some View {
        let searchShape = RoundedRectangle(cornerRadius: 19, style: .continuous)

        return HStack(spacing: 10) {
            HStack(spacing: 4) {
                ForEach(destinations, id: \.self) { destination in
                    navigationButton(for: destination)
                }
            }
            .padding(5)
            .frame(maxWidth: .infinity)
            .frame(height: dockHeight)
            .nativeGlassSurface(
                cornerRadius: 19,
                isInteractive: true,
                tint: AppTheme.glassTint
            )

            Button {
                searchHapticTrigger &+= 1
                searchAction()
            } label: {
                VStack(spacing: 4) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 21, weight: .black))
                    Text("Search")
                        .font(AppTheme.displayFont(.caption2))
                        .lineLimit(1)
                        .minimumScaleFactor(0.72)
                        .dynamicTypeSize(...DynamicTypeSize.large)
                }
                .foregroundStyle(AppTheme.textPrimary)
                .frame(width: searchWidth, height: dockHeight)
                .contentShape(.interaction, searchShape)
                .overlay(alignment: .top) {
                    Rectangle()
                        .fill(AppTheme.navigationAccent)
                        .frame(width: 26, height: 3)
                        .offset(y: 7)
                        .allowsHitTesting(false)
                }
            }
            .buttonStyle(AppNavigationPressStyle())
            .nativeGlassSurface(
                cornerRadius: 19,
                isInteractive: true,
                tint: AppTheme.glassSelectedTint
            )
            .contentShape(.interaction, searchShape)
            .sensoryFeedback(.impact(weight: .medium), trigger: searchHapticTrigger)
            .accessibilityLabel("Search current workspace")
            .accessibilityHint("Opens search in the last selected provider workspace")
            .accessibilityIdentifier("mainNavigation.search")
        }
        .background {
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture { }
                .accessibilityHidden(true)
        }
    }

    private var dockHeight: CGFloat {
        min(max(scaledDockHeight, 62), 76)
    }

    private var searchWidth: CGFloat {
        min(max(scaledSearchWidth, 60), 72)
    }

    private func navigationButton(for destination: MainTabDestination) -> some View {
        let isSelected = selection == destination
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)

        return Button {
            withAnimation(.smooth(duration: 0.24)) {
                selection = destination
            }
            selectionHapticTrigger &+= 1
        } label: {
            VStack(spacing: 4) {
                Image(systemName: destination.navigationSystemImage)
                    .font(.system(size: 19, weight: .black))
                    .symbolVariant(isSelected ? .fill : .none)
                Text(destination.navigationTitle)
                    .font(AppTheme.displayFont(.caption2))
                    .lineLimit(1)
                    .minimumScaleFactor(0.72)
                    .dynamicTypeSize(...DynamicTypeSize.large)
            }
            .foregroundStyle(isSelected ? AppTheme.signalForeground : AppTheme.textPrimary)
            .frame(maxWidth: .infinity, minHeight: dockHeight - 10)
            .background {
                if isSelected {
                    shape
                        .strokeBorder(AppTheme.hardShadow, lineWidth: 2)
                        .offset(x: 2, y: 3)
                    shape.fill(AppTheme.signalFill)
                    shape.strokeBorder(AppTheme.strokeStrong, lineWidth: 1.5)
                }
            }
            .overlay(alignment: .topTrailing) {
                if showsAboutBadge && destination == .about {
                    Circle()
                        .fill(AppTheme.danger)
                        .frame(width: 8, height: 8)
                        .overlay {
                            Circle().strokeBorder(AppTheme.strokeStrong, lineWidth: 1)
                        }
                        .offset(x: -6, y: 5)
                        .accessibilityHidden(true)
                }
            }
            .contentShape(shape)
        }
        .buttonStyle(AppNavigationPressStyle())
        .accessibilityLabel(destination.navigationTitle)
        .accessibilityValue(isSelected ? "Selected" : "")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityIdentifier("mainNavigation.\(destination.navigationIdentifier)")
    }
}

private struct AppNavigationPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .opacity(configuration.isPressed ? 0.78 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

private extension MainTabDestination {
    var navigationTitle: String {
        switch self {
        case .hosting: "Hosting"
        case .registrars: "Registrars"
        case .sites: "Sites"
        case .about: "About"
        case .search: "Search"
        }
    }

    var navigationSystemImage: String {
        switch self {
        case .hosting: "server.rack"
        case .registrars: "globe.americas"
        case .sites: "chart.xyaxis.line"
        case .about: "info.circle"
        case .search: "magnifyingglass"
        }
    }

    var navigationIdentifier: String {
        switch self {
        case .hosting: "hosting"
        case .registrars: "registrars"
        case .sites: "sites"
        case .about: "about"
        case .search: "search"
        }
    }
}

private extension View {
    /// Tab-bar visibility is a content preference. Applying it to every tab root
    /// ensures the system bar remains hidden while the custom Liquid Glass dock
    /// is the app's only primary navigation surface.
    func appTabContent() -> some View {
        toolbarVisibility(.hidden, for: .tabBar)
    }
}

private struct HostingEmptyStateView: View {
    @Environment(AuthManager.self) private var authManager
    @State private var showConnection = false

    var body: some View {
        NavigationStack {
            ZStack {
                AppTheme.canvas.ignoresSafeArea()

                AppAdaptiveEmptyStateContainer {
                    VStack(spacing: 12) {
                        if let error = authManager.error {
                            AppFeedbackBanner(
                                title: "Saved hosting accounts need attention",
                                message: error,
                                icon: "lock.trianglebadge.exclamationmark.fill",
                                tint: AppTheme.danger
                            )
                        }
                        AppEmptyState(
                            icon: "server.rack",
                            title: "No hosting account",
                            message: "Connect a hosting platform to see projects, deployments, logs, domains, and analytics.",
                            actionTitle: "Connect hosting"
                        ) {
                            showConnection = true
                        }
                    }
                }
            }
            .navigationTitle("Hosting")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                AppThemedToolbarItem(placement: .topBarLeading) {
                    ProviderAccountMenu()
                }
            }
            .sheet(isPresented: $showConnection) {
                LoginView(initialCategory: .hosting)
                    .presentationSizing(.page)
                    .presentationDragIndicator(.visible)
            }
        }
    }
}
