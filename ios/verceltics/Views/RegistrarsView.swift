import SwiftUI

@Observable
@MainActor
final class RegistrarDashboardViewModel {
    private struct CachedDomains {
        let domains: [RegistrarDomain]
        let updatedAt: Date
    }

    @ResettableMemoryCache private static var cachedDomains: [String: CachedDomains] = [:]
    private static let cacheLifetime = DashboardRefreshPolicy.inventoryFreshness

    let account: RegistrarAccount
    let api: RegistrarAPI
    var domains: [RegistrarDomain] = []
    var isLoading = true
    var isRefreshing = false
    var error: String?
    private var hasLoaded = false
    private var lastUpdatedAt: Date?
    private var loadGeneration = 0
    private var isRequestInFlight = false
    private let cacheKey: String

    init(account: RegistrarAccount) {
        self.account = account
        api = RegistrarAPI(account: account)
        cacheKey = CredentialCacheScope.registrarAccount(account)
        if let cached = Self.cachedDomains[cacheKey] {
            domains = cached.domains
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
            let loadedDomains = try await api.fetchDomains().sorted {
                $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
            }
            guard generation == loadGeneration else { return }
            let updatedAt = Date.now
            domains = loadedDomains
            hasLoaded = true
            lastUpdatedAt = updatedAt
            Self.cachedDomains[cacheKey] = CachedDomains(
                domains: loadedDomains,
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

struct RegistrarsView: View {
    @Environment(RegistrarStore.self) private var store
    @State private var showConnection = false
    var startWithSearch = false
    var searchRequestID = 0
    var backgroundRefreshRequestID = 0

    var body: some View {
        Group {
            if let account = store.activeAccount {
                RegistrarDashboardView(
                    account: account,
                    startWithSearch: startWithSearch,
                    searchRequestID: searchRequestID,
                    backgroundRefreshRequestID: backgroundRefreshRequestID
                )
                    .id(account.dashboardViewIdentity)
            } else {
                NavigationStack {
                    ZStack {
                        AppTheme.canvas.ignoresSafeArea()
                        VStack(spacing: 12) {
                            if let error = store.error {
                                AppFeedbackBanner(
                                    title: "Saved registrar accounts need attention",
                                    message: error,
                                    icon: "lock.trianglebadge.exclamationmark.fill",
                                    tint: AppTheme.danger
                                )
                            }
                            AppEmptyState(
                                icon: "globe.americas.fill",
                                title: "No registrar account",
                                message: "Connect a registrar to track expiry, renewal, privacy, locks, and nameservers.",
                                actionTitle: "Connect registrar"
                            ) {
                                showConnection = true
                            }
                        }
                        .padding(.horizontal, 16)
                        .frame(maxWidth: 560)
                    }
                    .navigationTitle("Registrars")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        AppThemedToolbarItem(placement: .topBarLeading) {
                            RegistrarAccountMenu()
                        }
                    }
                }
            }
        }
        .sheet(isPresented: $showConnection) {
            LoginView(initialCategory: .registrars)
                .presentationSizing(.page)
                .presentationDragIndicator(.visible)
        }
    }
}

private enum RegistrarProRoute: Hashable {
    case domain(String)
    case completeAPI
    case providerDashboard
}

struct RegistrarDashboardView: View {
    let account: RegistrarAccount
    var startWithSearch = false
    var searchRequestID = 0
    var backgroundRefreshRequestID = 0
    @State private var viewModel: RegistrarDashboardViewModel
    @State private var searchText = ""
    @State private var refreshSpin = 0.0
    @State private var proGate = ProAccessGate<RegistrarProRoute>()
    @State private var navigationRoute: RegistrarProRoute?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(RegistrarStore.self) private var store
    @Environment(PaywallManager.self) private var paywallManager

    init(
        account: RegistrarAccount,
        startWithSearch: Bool = false,
        searchRequestID: Int = 0,
        backgroundRefreshRequestID: Int = 0
    ) {
        self.account = account
        self.startWithSearch = startWithSearch
        self.searchRequestID = searchRequestID
        self.backgroundRefreshRequestID = backgroundRefreshRequestID
        _viewModel = State(initialValue: RegistrarDashboardViewModel(account: account))
    }

    private var provider: RegistrarProvider { account.provider }
    private var filteredDomains: [RegistrarDomain] {
        guard !searchText.isEmpty else { return viewModel.domains }
        return viewModel.domains.filter {
            $0.name.localizedCaseInsensitiveContains(searchText) || ($0.status?.localizedCaseInsensitiveContains(searchText) ?? false)
        }
    }
    private var expiringDomains: [RegistrarDomain] {
        viewModel.domains.filter { guard let days = $0.daysUntilExpiry else { return false }; return days <= 30 }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                AppTheme.canvas.ignoresSafeArea()
                if viewModel.isLoading {
                    AppDashboardLoadingView(accent: provider.accentColor)
                } else if let error = viewModel.error, viewModel.domains.isEmpty {
                    errorView(error)
                } else {
                    dashboard
                }
            }
            .navigationTitle("Registrars")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                AppThemedToolbarItem(placement: .topBarLeading) { RegistrarAccountMenu() }
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
                    .accessibilityLabel(viewModel.isRefreshing ? "Refreshing domains" : "Refresh domains")
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

    private var dashboard: some View {
        VStack(spacing: 0) {
            AppGlassSearchField(
                text: $searchText,
                prompt: "Search domains",
                startsFocused: startWithSearch,
                focusRequestID: searchRequestID
            )
            .padding(.horizontal, AppLayout.pagePadding(for: horizontalSizeClass))
            .padding(.top, 16)
            .padding(.bottom, 14)
            .appContentWidth(AppLayout.dashboardMaxWidth, horizontalSizeClass: horizontalSizeClass)

            ScrollView {
                LazyVStack(spacing: 16) {
                    portfolioHeader

                    if let error = store.error {
                        AppFeedbackBanner(
                            title: "Saved registrar change failed",
                            message: error,
                            icon: "lock.trianglebadge.exclamationmark.fill",
                            tint: AppTheme.danger
                        )
                    }
                    stats
                    actions

                    if let error = viewModel.error {
                        AppFeedbackBanner(
                            title: "Couldn’t refresh domains",
                            message: error,
                            actionTitle: "Try again"
                        ) {
                            Task { await viewModel.load(refresh: true) }
                        }
                    }

                    AppSectionHeader(title: "Domain portfolio", count: filteredDomains.count, accent: provider.accentColor)

                    if filteredDomains.isEmpty {
                        AppEmptyState(
                            icon: searchText.isEmpty ? "globe" : "magnifyingglass",
                            title: searchText.isEmpty ? "No domains returned" : "No matching domains",
                            message: searchText.isEmpty
                                ? "This registrar did not return any domains for the connected account."
                                : "Nothing matches “\(searchText)”."
                        )
                        .frame(maxWidth: .infinity)
                        .appSurface()
                    } else {
                        LazyVGrid(columns: domainColumns, spacing: 14) {
                            ForEach(filteredDomains) { domain in
                                Button {
                                    request(.domain(domain.id))
                                } label: { domainRow(domain) }
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

    private var domainColumns: [GridItem] {
        AppLayout.adaptiveColumns(
            for: horizontalSizeClass,
            regularMinimum: 340,
            regularMaximum: 540,
            spacing: 14
        )
    }

    private var portfolioHeader: some View {
        VStack(alignment: .leading, spacing: 17) {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 12) {
                    portfolioIdentity
                    AppStatusBadge(text: "Connected", tone: .success)
                }
            } else {
                HStack(spacing: 13) {
                    portfolioIdentity
                    Spacer()
                    AppStatusBadge(text: "Connected", tone: .success)
                }
            }

            VStack(alignment: .leading, spacing: 7) {
                HStack {
                    Text("EXPIRY HEALTH")
                        .font(AppTheme.displayFont(.caption2))
                        .tracking(1)
                        .foregroundStyle(AppTheme.textSecondary)
                    Spacer()
                    Text(expiryHealthLabel)
                        .font(.caption.weight(.bold))
                        .foregroundStyle(expiryHealthColor)
                }
                GeometryReader { geometry in
                    ZStack(alignment: .leading) {
                        Capsule().fill(AppTheme.skeletonStrong)
                        Capsule()
                            .fill(expiryHealthColor)
                            .frame(width: geometry.size.width * healthFraction)
                    }
                }
                .frame(height: 5)
            }
        }
        .padding(18)
        .providerSurface(accent: provider.accentColor)
    }

    private var portfolioIdentity: some View {
        HStack(spacing: 13) {
            RegistrarMark(provider: provider, size: 55)
            VStack(alignment: .leading, spacing: 4) {
                Text(account.name)
                    .font(AppTheme.displayFont(.title3))
                    .foregroundStyle(AppTheme.textPrimary)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 1)
                Text(provider.apiDescription)
                    .font(.footnote)
                    .foregroundStyle(AppTheme.textSecondary)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 2)
            }
            .layoutPriority(1)
        }
    }

    private var stats: some View {
        LazyVGrid(
            columns: statColumns,
            spacing: 10
        ) {
            statCard("Domains", value: viewModel.domains.count.formatted(), icon: "globe")
            statCard("Attention", value: expiringDomains.count.formatted(), icon: "calendar.badge.exclamationmark")
            statCard("Auto renew", value: viewModel.domains.filter { $0.autoRenew == true }.count.formatted(), icon: "arrow.triangle.2.circlepath")
        }
    }

    private var statColumns: [GridItem] {
        if dynamicTypeSize.isAccessibilitySize {
            return [GridItem(.flexible())]
        }
        if horizontalSizeClass == .regular {
            return Array(repeating: GridItem(.flexible(), spacing: 10), count: 3)
        }
        return [GridItem(.adaptive(minimum: 96), spacing: 10)]
    }

    private func statCard(_ title: String, value: String, icon: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            AppIconTile(icon: icon, tint: provider.accentColor, size: 28)
            Text(value).font(AppTheme.displayFont(.title2).monospacedDigit())
            Text(title.uppercased())
                .font(AppTheme.displayFont(.caption2))
                .tracking(0.6)
                .foregroundStyle(AppTheme.textSecondary)
                .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(13)
        .appSurface()
    }

    private var actions: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(spacing: 10) {
                    registrarActionButton(route: .providerDashboard, title: "Dashboard", icon: "safari.fill")
                    registrarActionButton(route: .completeAPI, title: "Complete API", icon: "list.bullet.rectangle.fill")
                }
            } else {
                HStack(spacing: 10) {
                    registrarActionButton(route: .providerDashboard, title: "Dashboard", icon: "safari.fill")
                    registrarActionButton(route: .completeAPI, title: "Complete API", icon: "list.bullet.rectangle.fill")
                }
            }
        }
        .buttonStyle(PressScaleButtonStyle())
        .frame(maxWidth: horizontalSizeClass == .regular ? 470 : .infinity, alignment: .leading)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func registrarActionButton(
        route: RegistrarProRoute,
        title: String,
        icon: String
    ) -> some View {
        Button {
            request(route)
        } label: {
            actionLabel(title, icon: icon)
        }
    }

    private func actionLabel(_ title: String, icon: String) -> some View {
        Label(title, systemImage: icon)
            .font(AppTheme.displayFont(.subheadline))
            .foregroundStyle(AppTheme.textPrimary)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 47)
            .padding(.vertical, dynamicTypeSize.isAccessibilitySize ? 8 : 0)
            .appSurface(raised: true)
    }

    @ViewBuilder
    private func destination(for route: RegistrarProRoute) -> some View {
        switch route {
        case .domain(let domainID):
            if let domain = viewModel.domains.first(where: { $0.id == domainID }) {
                RegistrarDomainDetailView(account: account, domain: domain)
            }
        case .completeAPI:
            ProviderFullAPICatalogView(account: account)
        case .providerDashboard:
            EmptyView()
        }
    }

    private func request(_ route: RegistrarProRoute) {
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

    private func perform(_ route: RegistrarProRoute) {
        switch route {
        case .domain(let domainID):
            guard viewModel.domains.contains(where: { $0.id == domainID }) else { return }
            navigationRoute = route
        case .completeAPI:
            navigationRoute = route
        case .providerDashboard:
            if let url = provider.dashboardURL {
                UIApplication.shared.open(url)
            }
        }
    }

    private func domainRow(_ domain: RegistrarDomain) -> some View {
        HStack(spacing: 13) {
            VStack(spacing: 1) {
                Text(expiryValue(domain))
                    .font(AppTheme.displayFont(.headline).monospacedDigit())
                Text(expiryUnit(domain)).font(AppTheme.displayFont(.caption2)).tracking(0.5)
            }
            .foregroundStyle(expiryColor(domain))
            .frame(width: 42, height: 42)
            .background(AppTheme.signalForeground)
            .clipShape(RoundedRectangle(cornerRadius: AppTheme.iconRadius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: AppTheme.iconRadius, style: .continuous)
                    .strokeBorder(AppTheme.strokeStrong, lineWidth: 1.25)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(domain.name).font(.subheadline.weight(.bold)).foregroundStyle(AppTheme.textPrimary).lineLimit(2)
                HStack(spacing: 7) {
                    if domain.autoRenew == true { Label("Auto", systemImage: "arrow.triangle.2.circlepath") }
                    if domain.locked == true { Label("Locked", systemImage: "lock.fill") }
                    if let date = domain.expiresAt { Text("Expires \(date.formatted(date: .abbreviated, time: .omitted))") }
                }
                .font(.caption2.weight(.semibold))
                .foregroundStyle(AppTheme.textSecondary)
            }
            .layoutPriority(1)
            Spacer()
            Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(AppTheme.textTertiary)
        }
        .padding(14)
        .appSurface()
        .accessibilityElement(children: .combine)
        .accessibilityHint("Open \(domain.name) details")
    }

    private var healthFraction: CGFloat {
        guard !viewModel.domains.isEmpty else { return 0 }
        return max(0, CGFloat(viewModel.domains.count - expiringDomains.count) / CGFloat(viewModel.domains.count))
    }

    private var expiryHealthLabel: String {
        guard !viewModel.domains.isEmpty else { return "No data" }
        let unknown = viewModel.domains.filter { $0.daysUntilExpiry == nil }.count
        if !expiringDomains.isEmpty { return "\(expiringDomains.count) need attention" }
        if unknown > 0 { return "\(unknown) unknown" }
        return "Clear"
    }

    private var expiryHealthColor: Color {
        guard !viewModel.domains.isEmpty else { return AppTheme.textTertiary }
        if !expiringDomains.isEmpty { return AppTheme.warning }
        if viewModel.domains.contains(where: { $0.daysUntilExpiry == nil }) { return AppTheme.textSecondary }
        return AppTheme.success
    }

    private func expiryColor(_ domain: RegistrarDomain) -> Color {
        guard let days = domain.daysUntilExpiry else { return provider.accentColor }
        if days < 0 { return AppTheme.danger }
        if days <= 30 { return AppTheme.warning }
        return AppTheme.success
    }

    private func expiryValue(_ domain: RegistrarDomain) -> String {
        guard let days = domain.daysUntilExpiry else { return "—" }
        return abs(days).formatted()
    }

    private func expiryUnit(_ domain: RegistrarDomain) -> String {
        guard let days = domain.daysUntilExpiry else { return "UNKNOWN" }
        return days < 0 ? "EXPIRED" : "DAYS"
    }

    private func errorView(_ message: String) -> some View {
        AppEmptyState(
            icon: "exclamationmark.triangle.fill",
            title: "Could not load domains",
            message: message,
            actionTitle: "Try again"
        ) {
            Task { await viewModel.load(refresh: true) }
        }
    }
}

private extension RegistrarAccount {
    var dashboardViewIdentity: String {
        let metadataValue = metadata
            .sorted { $0.key < $1.key }
            .map { "\($0.key)=\($0.value)" }
            .joined(separator: "&")
        return "\(id.uuidString)|\(primaryCredential.hashValue)|\(secondaryCredential?.hashValue ?? 0)|\(metadataValue.hashValue)"
    }
}
