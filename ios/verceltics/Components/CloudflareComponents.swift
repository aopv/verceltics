import SwiftUI

enum CloudflareStyle {
    static let paper = Color(red: 1.0, green: 0.985, blue: 0.94)
    static let orange = Color(red: 1.00, green: 0.39, blue: 0.035)
    static let orangeLight = Color(red: 1.00, green: 0.56, blue: 0.16)
    static let amber = Color(red: 1.00, green: 0.72, blue: 0.18)
    static let noticeYellow = Color(red: 1.00, green: 0.83, blue: 0.16)
    static let lime = Color(red: 0.47, green: 0.90, blue: 0.25)
    static let green = AppTheme.success
    static let red = AppTheme.danger
}

struct CloudflareInkTile: View {
    let icon: String
    var glyphTint: Color = CloudflareStyle.paper
    var size: CGFloat = 36

    var body: some View {
        Image(systemName: icon)
            .font(.system(size: size * 0.40, weight: .black))
            .foregroundStyle(glyphTint)
            .frame(width: size, height: size)
            .background(AppTheme.signalForeground)
            .clipShape(RoundedRectangle(cornerRadius: AppTheme.iconRadius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: AppTheme.iconRadius, style: .continuous)
                    .strokeBorder(AppTheme.strokeStrong, lineWidth: 1.5)
            }
            .accessibilityHidden(true)
    }
}

struct CloudflarePanelModifier: ViewModifier {
    var accentOpacity: Double = 0

    @ViewBuilder
    func body(content: Content) -> some View {
        if accentOpacity > 0 {
            content
                .background {
                    LinearGradient(
                        colors: [CloudflareStyle.orange.opacity(accentOpacity), .clear],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                }
                .appSurface()
        } else {
            content.appSurface()
        }
    }
}

extension View {
    func cloudflarePanel(accentOpacity: Double = 0) -> some View {
        modifier(CloudflarePanelModifier(accentOpacity: accentOpacity))
    }
}

struct CloudflareSectionHeader: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    let title: String
    let icon: String
    var count: Int?
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 10) {
                    titleLabel
                    HStack(spacing: 8) {
                        countBadge
                        Spacer(minLength: 8)
                        actionButton
                    }
                }
            } else {
                HStack(spacing: 8) {
                    titleLabel
                    Spacer(minLength: 8)
                    countBadge
                    actionButton
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }

    private var titleLabel: some View {
        HStack(alignment: .top, spacing: 8) {
            CloudflareInkTile(icon: icon, size: 28)

            Text(title)
                .font(AppTheme.displayFont(.title3))
                .foregroundStyle(AppTheme.textPrimary)
                .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 2)
        }
    }

    @ViewBuilder
    private var countBadge: some View {
        if let count {
            Text(count.formatted())
                .font(AppTheme.displayFont(.caption).monospacedDigit())
                .foregroundStyle(CloudflareStyle.orange)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(AppTheme.signalForeground)
                .clipShape(RoundedRectangle(cornerRadius: 3, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 3, style: .continuous)
                        .strokeBorder(AppTheme.strokeStrong, lineWidth: 1)
                }
        }
    }

    @ViewBuilder
    private var actionButton: some View {
        if let actionTitle, let action {
            Button(actionTitle, action: action)
                .font(.footnote.weight(.bold))
                .foregroundStyle(AppTheme.signal)
                .buttonStyle(.plain)
                .frame(minHeight: 44)
        }
    }
}

struct CloudflareResourceRow<Trailing: View>: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    let icon: String
    let title: String
    let subtitle: String?
    var tint: Color = CloudflareStyle.orange
    @ViewBuilder let trailing: () -> Trailing

    var body: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 10) {
                    identity
                    HStack {
                        Spacer(minLength: 48)
                        trailing()
                    }
                }
            } else {
                HStack(spacing: 12) {
                    identity
                    Spacer(minLength: 8)
                    trailing()
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 13)
        .frame(minHeight: 64)
        .contentShape(Rectangle())
    }

    private var identity: some View {
        HStack(alignment: .top, spacing: 12) {
            CloudflareInkTile(icon: icon, glyphTint: tint)

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(AppTheme.displayFont(.headline))
                    .foregroundStyle(AppTheme.textPrimary)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 2)

                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.footnote)
                        .foregroundStyle(AppTheme.textPrimary.opacity(0.76))
                        .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 2)
                        .truncationMode(.middle)
                }
            }
        }
    }
}

struct CloudflareChevron: View {
    var body: some View {
        Image(systemName: "chevron.right")
            .font(.caption.weight(.black))
            .foregroundStyle(AppTheme.textPrimary)
    }
}

extension CloudflareResourceRow where Trailing == CloudflareChevron {
    init(icon: String, title: String, subtitle: String?, tint: Color = CloudflareStyle.orange) {
        self.init(icon: icon, title: title, subtitle: subtitle, tint: tint) {
            CloudflareChevron()
        }
    }
}

struct CloudflareStatusPill: View {
    let text: String
    var color: Color
    var fillColor: Color?

    init(text: String, color: Color, fillColor: Color? = nil) {
        self.text = text
        self.color = color
        self.fillColor = fillColor
    }

    var body: some View {
        HStack(spacing: 5) {
            Circle()
                .fill(fillColor == nil ? color : AppTheme.signalForeground)
                .frame(width: 5, height: 5)
            Text(text)
                .font(AppTheme.displayFont(.caption2))
                .lineLimit(1)
        }
        .foregroundStyle(fillColor == nil ? AppTheme.textPrimary : AppTheme.signalForeground)
        .padding(.horizontal, 9)
        .padding(.vertical, 6)
        .background(fillColor ?? color.opacity(0.16))
        .clipShape(RoundedRectangle(cornerRadius: 2, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 2, style: .continuous)
                .strokeBorder(fillColor == nil ? color : AppTheme.strokeStrong, lineWidth: 1.5)
        }
    }
}

struct CloudflareMetricCard: View {
    let title: String
    let value: String
    let icon: String
    var accent: Color = CloudflareStyle.orange

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.caption2.weight(.semibold))
                Text(title.uppercased())
                    .font(AppTheme.displayFont(.caption2))
                    .tracking(0.8)
            }
            .foregroundStyle(AppTheme.textSecondary)

            Text(value)
                .font(AppTheme.displayFont(.title2).monospacedDigit())
                .foregroundStyle(AppTheme.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.65)
                .contentTransition(.numericText())

            Rectangle()
                .fill(accent)
                .frame(width: 30, height: 3)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .cloudflarePanel(accentOpacity: 0.045)
    }
}

struct CloudflareEmptySection: View {
    let icon: String
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: 9) {
            AppIconTile(icon: icon, tint: AppTheme.textTertiary, size: 40)
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(AppTheme.textPrimary)
            Text(message)
                .font(.footnote)
                .foregroundStyle(AppTheme.textSecondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 24)
        .padding(.vertical, 28)
    }
}

struct CloudflareLoadingView: View {
    var body: some View {
        AppDashboardLoadingView(accent: CloudflareStyle.orange)
    }
}

struct CloudflareErrorView: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        AppEmptyState(
            icon: "exclamationmark.triangle.fill",
            title: "Cloudflare couldn’t load",
            message: message,
            actionTitle: "Try again",
            action: retry
        )
    }
}

struct CloudflareEdgeHeader: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    let accountName: String
    let email: String
    let zones: Int
    let pages: Int
    let workers: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 12) {
                    accountIdentity
                    connectedBadge
                }
            } else {
                HStack(alignment: .top, spacing: 13) {
                    accountIdentity
                    Spacer(minLength: 8)
                    connectedBadge
                }
            }

            if dynamicTypeSize.isAccessibilitySize {
                VStack(spacing: 9) {
                    edgeNode(value: zones, title: "ZONES", icon: "globe")
                    edgeNode(value: pages, title: "PAGES", icon: "doc.badge.gearshape", emphasized: true)
                    edgeNode(value: workers, title: "WORKERS", icon: "shippingbox.fill")
                }
            } else {
                HStack(spacing: 9) {
                    edgeNode(value: zones, title: "ZONES", icon: "globe")
                    edgeNode(value: pages, title: "PAGES", icon: "doc.badge.gearshape", emphasized: true)
                    edgeNode(value: workers, title: "WORKERS", icon: "shippingbox.fill")
                }
            }
        }
        .padding(18)
        .background {
            RoundedRectangle(cornerRadius: AppTheme.panelRadius, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [CloudflareStyle.orangeLight, CloudflareStyle.orange],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
        }
        .clipShape(RoundedRectangle(cornerRadius: AppTheme.panelRadius, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: AppTheme.panelRadius, style: .continuous)
                .strokeBorder(AppTheme.signalForeground, lineWidth: 2)
        }
        .background {
            RoundedRectangle(cornerRadius: AppTheme.panelRadius, style: .continuous)
                .fill(AppTheme.shadow)
                .offset(x: 4, y: 4)
        }
    }

    private var accountIdentity: some View {
        HStack(alignment: .top, spacing: 13) {
            ProviderMark(provider: .cloudflare, size: 29)
                .frame(width: 52, height: 52)
                .background(AppTheme.signalForeground)
                .clipShape(RoundedRectangle(cornerRadius: AppTheme.iconRadius, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: AppTheme.iconRadius, style: .continuous)
                        .strokeBorder(AppTheme.signalForeground, lineWidth: 1.5)
                }

            VStack(alignment: .leading, spacing: 4) {
                Text(accountName)
                    .font(AppTheme.displayFont(.title))
                    .foregroundStyle(AppTheme.signalForeground)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 2)
                    .minimumScaleFactor(dynamicTypeSize.isAccessibilitySize ? 1 : 0.72)
                Text(email)
                    .font(.footnote)
                    .foregroundStyle(AppTheme.signalForeground.opacity(0.72))
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? 2 : 1)
                    .truncationMode(.middle)
            }
        }
    }

    private var connectedBadge: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(AppTheme.signalForeground)
                .frame(width: 6, height: 6)
            Text("Connected")
                .font(AppTheme.displayFont(.caption2))
                .lineLimit(1)
        }
        .foregroundStyle(AppTheme.signalForeground)
        .padding(.horizontal, 9)
        .padding(.vertical, 7)
        .background(CloudflareStyle.lime)
        .clipShape(RoundedRectangle(cornerRadius: 2, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 2, style: .continuous)
                .strokeBorder(AppTheme.signalForeground, lineWidth: 1.5)
        }
    }

    private func edgeNode(value: Int, title: String, icon _: String, emphasized: Bool = false) -> some View {
        VStack(spacing: 7) {
            Text(value.formatted())
                .font(AppTheme.displayFont(.largeTitle).monospacedDigit())
                .foregroundStyle(AppTheme.signalForeground)
                .minimumScaleFactor(0.65)

            Rectangle()
                .fill(AppTheme.signalForeground)
                .frame(width: 54, height: 1.5)

            Text(title)
                .font(AppTheme.displayFont(.caption))
                .tracking(0.7)
                .foregroundStyle(AppTheme.signalForeground)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 5)
        .padding(.vertical, 12)
        .background(
            emphasized
                ? Color(red: 1.0, green: 0.985, blue: 0.94)
                : CloudflareStyle.orangeLight
        )
        .clipShape(RoundedRectangle(cornerRadius: 2, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 2, style: .continuous)
                .strokeBorder(AppTheme.signalForeground, lineWidth: 1.5)
        }
        .background {
            RoundedRectangle(cornerRadius: 2, style: .continuous)
                .fill(AppTheme.signalForeground)
                .offset(x: 2, y: 3)
        }
    }

}

struct CloudflareWriteNotice: View {
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "lock.shield.fill")
                .font(.system(size: 15, weight: .black))
                .foregroundStyle(CloudflareStyle.amber)
                .frame(width: 36, height: 36)
                .background(AppTheme.signalForeground)
                .clipShape(RoundedRectangle(cornerRadius: AppTheme.iconRadius, style: .continuous))
            VStack(alignment: .leading, spacing: 3) {
                Text("Write access is guarded")
                    .font(AppTheme.displayFont(.headline))
                    .foregroundStyle(AppTheme.signalForeground)
                Text("Changes use the connected Cloudflare credential. Destructive actions always ask for confirmation.")
                    .font(.footnote)
                    .foregroundStyle(AppTheme.signalForeground.opacity(0.82))
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .background {
            LinearGradient(
                colors: [Color(red: 1.0, green: 0.89, blue: 0.25), CloudflareStyle.noticeYellow],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
        .clipShape(RoundedRectangle(cornerRadius: AppTheme.panelRadius, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: AppTheme.panelRadius, style: .continuous)
                .strokeBorder(AppTheme.strokeStrong, lineWidth: 2)
        )
        .background {
            RoundedRectangle(cornerRadius: AppTheme.panelRadius, style: .continuous)
                .fill(AppTheme.shadow)
                .offset(x: 4, y: 4)
        }
    }
}

struct CloudflareActionButton: View {
    let title: String
    let icon: String
    var role: ButtonRole?
    var isWorking = false
    let action: () -> Void

    private var accent: Color {
        role == .destructive ? CloudflareStyle.red : CloudflareStyle.orange
    }

    private var foreground: Color {
        role == .destructive ? AppTheme.danger : AppTheme.signal
    }

    var body: some View {
        Button(role: role, action: action) {
            HStack(spacing: 7) {
                if isWorking {
                    ProgressView()
                        .controlSize(.small)
                        .tint(foreground)
                } else {
                    Image(systemName: icon)
                        .font(.system(size: 10, weight: .semibold))
                }
                Text(title)
                    .font(.footnote.weight(.bold))
            }
            .foregroundStyle(foreground)
            .padding(.horizontal, 12)
            .frame(minHeight: 44)
            .background(accent.opacity(0.10))
            .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .strokeBorder(accent, lineWidth: 1.25)
            }
        }
        .buttonStyle(PressScaleButtonStyle())
        .disabled(isWorking)
    }
}

struct CloudflareActionResultBanner: View {
    let message: String
    var isError = false

    private var tint: Color { isError ? CloudflareStyle.red : CloudflareStyle.green }

    var body: some View {
        HStack(spacing: 9) {
            Image(systemName: isError ? "exclamationmark.circle.fill" : "checkmark.circle.fill")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(tint)
            Text(message)
                .font(.footnote.weight(.medium))
                .foregroundStyle(AppTheme.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .background(tint.opacity(0.075))
        .clipShape(RoundedRectangle(cornerRadius: 7, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 7, style: .continuous)
                .strokeBorder(tint, lineWidth: 1)
        )
    }
}

struct CloudflareDetailRow: View {
    let icon: String
    let title: String
    let value: String
    var valueColor: Color = AppTheme.textPrimary

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textTertiary)
                .frame(width: 18)

            VStack(alignment: .leading, spacing: 2) {
                Text(title.uppercased())
                    .font(.caption2.weight(.semibold))
                    .tracking(0.7)
                    .foregroundStyle(AppTheme.textSecondary)
                Text(value)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(valueColor)
                    .lineLimit(2)
                    .truncationMode(.middle)
                    .textSelection(.enabled)
            }

            Spacer(minLength: 8)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 11)
    }
}

struct CloudflareSearchEmptyView: View {
    let searchText: String

    var body: some View {
        CloudflareEmptySection(
            icon: "magnifyingglass",
            title: "No matches",
            message: "Nothing in this Cloudflare account matches “\(searchText)”."
        )
        .cloudflarePanel()
    }
}
