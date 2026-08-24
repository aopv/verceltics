import SwiftUI

@Observable
@MainActor
final class HostingDashboardViewModel {
    private struct CachedResources {
        let resources: [HostingResource]
        let updatedAt: Date
    }

    @ResettableMemoryCache private static var cachedResources: [String: CachedResources] = [:]
    private static let cacheLifetime = DashboardRefreshPolicy.inventoryFreshness

    let account: VercelAccount
    let api: HostingProviderAPI
    var resources: [HostingResource] = []
    var isLoading = true
    var isRefreshing = false
    var error: String?
    private var hasLoaded = false
    private var lastUpdatedAt: Date?
    private var loadGeneration = 0
    private var isRequestInFlight = false
    private let cacheKey: String

    init(account: VercelAccount) {
        self.account = account
        self.api = HostingProviderAPI(account: account)
        cacheKey = CredentialCacheScope.hostingAccount(account)
        if let cached = Self.cachedResources[cacheKey] {
            resources = cached.resources
            lastUpdatedAt = cached.updatedAt
            isLoading = false
            hasLoaded = true
        }
    }

    func load(refresh: Bool = false) async {
        if !refresh,
           let lastUpdatedAt,
           Date.now.timeIntervalSince(lastUpdatedAt) < Self.cacheLifetime {
            return
        }
        guard !isRequestInFlight else { return }
        isRequestInFlight = true
        loadGeneration += 1
        let generation = loadGeneration
        isLoading = !hasLoaded
        isRefreshing = hasLoaded
        error = nil
        defer {
            if generation == loadGeneration {
                isRequestInFlight = false
                isLoading = false
                isRefreshing = false
            }
        }
        do {
            let loadedResources = try await api.fetchResources().sorted {
                $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
            }
            guard generation == loadGeneration else { return }
            let updatedAt = Date.now
            resources = loadedResources
            hasLoaded = true
            lastUpdatedAt = updatedAt
            Self.cachedResources[cacheKey] = CachedResources(
                resources: loadedResources,
                updatedAt: updatedAt
            )
        } catch is CancellationError {
            // Switching tabs can cancel a request; keep any cached content.
        } catch {
            guard generation == loadGeneration else { return }
            self.error = error.localizedDescription
        }
    }
}

private enum HostingProRoute: Hashable {
    case resource(String)
    case completeAPI
    case providerDashboard
}

struct HostingDashboardView: View {
    let account: VercelAccount
    var startWithSearch = false
    var searchRequestID = 0
    var backgroundRefreshRequestID = 0

    @State private var viewModel: HostingDashboardViewModel
    @State private var searchText = ""
    @State private var refreshSpin = 0.0
    @State private var proGate = ProAccessGate<HostingProRoute>()
    @State private var navigationRoute: HostingProRoute?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(AuthManager.self) private var authManager
    @Environment(PaywallManager.self) private var paywallManager

    init(
        account: VercelAccount,
        startWithSearch: Bool = false,
        searchRequestID: Int = 0,
        backgroundRefreshRequestID: Int = 0
    ) {
        self.account = account
        self.startWithSearch = startWithSearch
        self.searchRequestID = searchRequestID
        self.backgroundRefreshRequestID = backgroundRefreshRequestID
        _viewModel = State(initialValue: HostingDashboardViewModel(account: account))
    }

    private var provider: AccountProvider { account.provider }

    private var filteredResources: [HostingResource] {
        guard !searchText.isEmpty else { return viewModel.resources }
        return viewModel.resources.filter {
            $0.name.localizedCaseInsensitiveContains(searchText)
                || ($0.subtitle?.localizedCaseInsensitiveContains(searchText) ?? false)
                || ($0.status?.localizedCaseInsensitiveContains(searchText) ?? false)
                || ($0.kind?.localizedCaseInsensitiveContains(searchText) ?? false)
        }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                AppTheme.canvas.ignoresSafeArea()
                if viewModel.isLoading {
                    AppDashboardLoadingView(accent: provider.accentColor, showsMetrics: false)
                } else if let error = viewModel.error, viewModel.resources.isEmpty {
                    errorView(error)
                } else {
                    content
                }
            }
            .navigationTitle(provider.displayName)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                AppThemedToolbarItem(placement: .topBarLeading) { ProviderAccountMenu() }
                AppThemedToolbarItem(placement: .topBarTrailing) {
                    Button {
                        if !reduceMotion {
                            withAnimation(.easeInOut(duration: 0.45)) { refreshSpin += 360 }
                        }
                        Task { await viewModel.load(refresh: true) }
                    } label: {
                        AppToolbarActionLabel(
                            systemImage: "arrow.clockwise",
                            rotation: refreshSpin,
                            isBusy: viewModel.isRefreshing
                        )
                    }
                    .disabled(viewModel.isRefreshing)
                    .accessibilityLabel(viewModel.isRefreshing ? "Refreshing \(provider.displayName)" : "Refresh \(provider.displayName)")
                    .accessibilityIdentifier("topbar.refresh.hosting")
                }
            }
            .task { await viewModel.load() }
            .onChange(of: backgroundRefreshRequestID) { _, _ in
                Task { await viewModel.load() }
            }
            .navigationDestination(item: $navigationRoute) { route in
                destination(for: route)
            }
            .proPaywall(
                isPresented: $proGate.isPaywallPresented,
                onDismiss: handlePaywallDismiss
            )
        }
    }

    private var content: some View {
        VStack(spacing: 0) {
            AppGlassSearchField(
                text: $searchText,
                prompt: "Search \(provider.displayName)",
                startsFocused: startWithSearch,
                focusRequestID: searchRequestID
            )
            .padding(.horizontal, AppLayout.pagePadding(for: horizontalSizeClass))
            .padding(.top, 16)
            .padding(.bottom, 14)
            .appContentWidth(AppLayout.dashboardMaxWidth, horizontalSizeClass: horizontalSizeClass)

            ScrollView {
                LazyVStack(spacing: 16) {
                    accountCard

                    if let error = authManager.error {
                        AppFeedbackBanner(
                            title: "Saved account change failed",
                            message: error,
                            icon: "lock.trianglebadge.exclamationmark.fill",
                            tint: AppTheme.danger
                        )
                    }

                    if let error = viewModel.error {
                        AppFeedbackBanner(
                            title: "Couldn’t refresh \(provider.displayName)",
                            message: error,
                            actionTitle: "Try again"
                        ) {
                            Task { await viewModel.load(refresh: true) }
                        }
                    }

                    actionGrid

                    AppSectionHeader(title: resourceTitle, count: filteredResources.count, accent: provider.accentColor)

                    if filteredResources.isEmpty {
                        AppEmptyState(
                            icon: searchText.isEmpty ? provider.systemImage : "magnifyingglass",
                            title: searchText.isEmpty ? "No resources returned" : "No matching resources",
                            message: searchText.isEmpty
                                ? "This provider did not return any \(resourceTitle.lowercased()) for the connected account."
                                : "Nothing matches “\(searchText)”."
                        )
                        .frame(maxWidth: .infinity)
                        .appSurface()
                    } else {
                        LazyVGrid(columns: resourceColumns, spacing: 14) {
                            ForEach(filteredResources) { resource in
                                Button {
                                    request(.resource(resource.id))
                                } label: {
                                    resourceRow(resource)
                                }
                                .buttonStyle(PressScaleButtonStyle())
                            }
                        }
                    }
                }
                .padding(.horizontal, AppLayout.pagePadding(for: horizontalSizeClass))
                .padding(.top, 4)
                .padding(.bottom, 24)
                .appContentWidth(AppLayout.dashboardMaxWidth, horizontalSizeClass: horizontalSizeClass)
            }
            .refreshable { await viewModel.load(refresh: true) }
            .scrollDismissesKeyboard(.interactively)
        }
    }

    private var resourceColumns: [GridItem] {
        AppLayout.adaptiveColumns(
            for: horizontalSizeClass,
            regularMinimum: 340,
            regularMaximum: 540,
            spacing: 14
        )
    }

    private var accountCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            if dynamicTypeSize.isAccessibilitySize {
                accountIdentity
                AppStatusBadge(text: "Connected", tone: .success)
            } else {
                HStack(spacing: 14) {
                    accountIdentity
                    Spacer()
                    AppStatusBadge(text: "Connected", tone: .success)
                }
            }
        }
        .padding(18)
        .providerSurface(accent: provider.accentColor)
    }

    private var accountIdentity: some View {
        HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: AppTheme.iconRadius, style: .continuous)
                    .fill(provider == .vercel ? AppTheme.surface : AppTheme.signalForeground)
                if let avatar = account.avatarURL, let url = URL(string: avatar) {
                    AsyncImage(url: url) { image in image.resizable().scaledToFill() } placeholder: {
                        ProviderMark(provider: provider, size: 28)
                    }
                } else {
                    ProviderMark(provider: provider, size: 28)
                }
            }
            .frame(width: 56, height: 56)
            .clipShape(RoundedRectangle(cornerRadius: AppTheme.iconRadius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: AppTheme.iconRadius, style: .continuous)
                    .strokeBorder(AppTheme.strokeStrong, lineWidth: 1.25)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(account.name)
                    .font(AppTheme.displayFont(.title3))
                    .foregroundStyle(AppTheme.textPrimary)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 1)
                Text(account.email ?? provider.connectionSubtitle)
                    .font(.footnote)
                    .foregroundStyle(AppTheme.textSecondary)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? 2 : 1)
            }
            .layoutPriority(1)
        }
    }

    private var actionGrid: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(spacing: 10) {
                    dashboardButton(route: .providerDashboard, icon: "safari.fill", title: "Dashboard")
                    dashboardButton(route: .completeAPI, icon: "list.bullet.rectangle.fill", title: "Complete API")
                }
            } else {
                HStack(spacing: 10) {
                    dashboardButton(route: .providerDashboard, icon: "safari.fill", title: "Dashboard")
                    dashboardButton(route: .completeAPI, icon: "list.bullet.rectangle.fill", title: "Complete API")
                }
            }
        }
        .buttonStyle(PressScaleButtonStyle())
        .frame(maxWidth: horizontalSizeClass == .regular ? 470 : .infinity, alignment: .leading)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func dashboardButton(route: HostingProRoute, icon: String, title: String) -> some View {
        Button {
            request(route)
        } label: {
            dashboardAction(icon: icon, title: title)
        }
    }

    private func dashboardAction(icon: String, title: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .foregroundStyle(provider.accentColor)
            Text(title).font(AppTheme.displayFont(.subheadline))
        }
        .frame(maxWidth: .infinity)
        .frame(minHeight: 48)
        .padding(.vertical, dynamicTypeSize.isAccessibilitySize ? 8 : 0)
        .appSurface(raised: true)
    }

    @ViewBuilder
    private func destination(for route: HostingProRoute) -> some View {
        switch route {
        case .resource(let resourceID):
            if let resource = viewModel.resources.first(where: { $0.id == resourceID }) {
                HostingResourceDetailView(account: account, resource: resource)
            }
        case .completeAPI:
            ProviderFullAPICatalogView(account: account)
        case .providerDashboard:
            EmptyView()
        }
    }

    private func request(_ route: HostingProRoute) {
        if let route = proGate.request(
            route,
            hasProAccess: paywallManager.hasActiveSubscription
        ) {
            perform(route)
        }
    }

    private func handlePaywallDismiss() {
        if let route = proGate.resumeAfterDismiss(
            hasProAccess: paywallManager.hasActiveSubscription
        ) {
            perform(route)
        }
    }

    private func perform(_ route: HostingProRoute) {
        switch route {
        case .resource(let resourceID):
            guard viewModel.resources.contains(where: { $0.id == resourceID }) else { return }
            navigationRoute = route
        case .completeAPI:
            navigationRoute = route
        case .providerDashboard:
            if let url = viewModel.api.dashboardURL() {
                UIApplication.shared.open(url)
            }
        }
    }

    private func resourceRow(_ resource: HostingResource) -> some View {
        HStack(spacing: 13) {
            AppIconTile(icon: resourceIcon(resource), tint: provider.accentColor, size: 40)
            VStack(alignment: .leading, spacing: 4) {
                Text(resource.name)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(AppTheme.textPrimary)
                    .lineLimit(2)
                Text([resource.kind, resource.region, resource.subtitle].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · "))
                    .font(.footnote)
                    .foregroundStyle(AppTheme.textSecondary)
                    .lineLimit(2)
            }
            .layoutPriority(1)
            Spacer()
            if let status = resource.status {
                AppStatusBadge(text: status.capitalized, tone: .status(status))
            }
            Image(systemName: "chevron.right")
                .font(.system(size: 10, weight: .semibold))
                .foregroundStyle(AppTheme.textTertiary)
        }
        .padding(15)
        .appSurface()
        .accessibilityElement(children: .combine)
        .accessibilityHint("Open \(resource.name) details")
    }

    private func errorView(_ message: String) -> some View {
        AppEmptyState(
            icon: "exclamationmark.triangle.fill",
            title: "Could not load \(provider.displayName)",
            message: message,
            actionTitle: "Try again"
        ) {
            Task { await viewModel.load(refresh: true) }
        }
    }

    private var resourceTitle: String {
        switch provider {
        case .netlify, .firebase: "Sites"
        case .railway: "Projects"
        case .render: "Services"
        case .digitalOcean, .heroku, .fly, .awsAmplify: "Apps"
        default: "Resources"
        }
    }

    private func resourceIcon(_ resource: HostingResource) -> String {
        switch provider {
        case .netlify, .firebase: "globe"
        case .railway: "shippingbox.fill"
        case .render: "server.rack"
        case .digitalOcean, .heroku, .fly, .awsAmplify: "app.fill"
        default: provider.systemImage
        }
    }
}

struct ProviderPanelModifier: ViewModifier {
    let accent: Color

    func body(content: Content) -> some View {
        content.appSurface()
    }
}

extension View {
    func providerPanel(accent: Color) -> some View { modifier(ProviderPanelModifier(accent: accent)) }
}

func statusColor(_ status: String) -> Color {
    AppStatusTone.status(status).color
}
