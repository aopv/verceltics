import SwiftUI
import UIKit

/// Verceltics 3's "Soft Neo Utility" visual language. Content uses warm paper,
/// strong ink outlines, signal orange, and restrained offset depth. Navigation
/// chrome uses the same language while retaining native Liquid Glass optics.
enum AppTheme {
    private static func adaptive(light: UIColor, dark: UIColor) -> Color {
        Color(uiColor: UIColor { traits in
            traits.userInterfaceStyle == .dark ? dark : light
        })
    }

    static let canvas = adaptive(
        light: UIColor(red: 0.973, green: 0.953, blue: 0.910, alpha: 1),
        dark: UIColor(red: 0.047, green: 0.042, blue: 0.034, alpha: 1)
    )
    static let surface = adaptive(
        light: UIColor(red: 1.0, green: 0.992, blue: 0.965, alpha: 1),
        dark: UIColor(red: 0.105, green: 0.092, blue: 0.073, alpha: 1)
    )
    static let surfaceRaised = adaptive(
        light: UIColor(red: 0.927, green: 0.890, blue: 0.822, alpha: 1),
        dark: UIColor(red: 0.157, green: 0.137, blue: 0.108, alpha: 1)
    )
    static let textPrimary = adaptive(
        light: UIColor(red: 0.055, green: 0.049, blue: 0.039, alpha: 1),
        dark: UIColor(red: 0.975, green: 0.949, blue: 0.894, alpha: 1)
    )
    static let textSecondary = adaptive(
        light: UIColor(red: 0.36, green: 0.325, blue: 0.274, alpha: 1),
        dark: UIColor(red: 0.73, green: 0.687, blue: 0.614, alpha: 1)
    )
    static let textTertiary = adaptive(
        light: UIColor(red: 0.416, green: 0.373, blue: 0.314, alpha: 1),
        dark: UIColor(red: 0.54, green: 0.497, blue: 0.425, alpha: 1)
    )
    static let stroke = adaptive(
        light: UIColor.black.withAlphaComponent(0.94),
        dark: UIColor(red: 0.975, green: 0.949, blue: 0.894, alpha: 0.42)
    )
    static let strokeStrong = adaptive(
        light: UIColor.black,
        dark: UIColor(red: 0.975, green: 0.949, blue: 0.894, alpha: 0.64)
    )
    static let strokeSoft = adaptive(
        light: UIColor.black.withAlphaComponent(0.25),
        dark: UIColor(red: 0.975, green: 0.949, blue: 0.894, alpha: 0.22)
    )
    static let divider = adaptive(
        light: UIColor.black.withAlphaComponent(0.32),
        dark: UIColor(red: 0.975, green: 0.949, blue: 0.894, alpha: 0.25)
    )
    static let inkRule = adaptive(
        light: UIColor.black.withAlphaComponent(0.78),
        dark: UIColor(red: 0.975, green: 0.949, blue: 0.894, alpha: 0.58)
    )
    static let signal = adaptive(
        light: UIColor(red: 0.698, green: 0.227, blue: 0.0, alpha: 1),
        dark: UIColor(red: 1.0, green: 0.52, blue: 0.19, alpha: 1)
    )
    static let navigationAccent = adaptive(
        light: UIColor(red: 0.77, green: 0.205, blue: 0.0, alpha: 1),
        dark: UIColor(red: 1.0, green: 0.52, blue: 0.19, alpha: 1)
    )
    static let signalFill = adaptive(
        light: UIColor(red: 1.0, green: 0.38, blue: 0.035, alpha: 1),
        dark: UIColor(red: 1.0, green: 0.52, blue: 0.19, alpha: 1)
    )
    static let signalForeground = adaptive(
        light: UIColor(red: 0.045, green: 0.040, blue: 0.032, alpha: 1),
        dark: UIColor(red: 0.045, green: 0.040, blue: 0.032, alpha: 1)
    )
    static let success = adaptive(
        light: UIColor(red: 0.078, green: 0.451, blue: 0.192, alpha: 1),
        dark: UIColor(red: 0.38, green: 0.86, blue: 0.44, alpha: 1)
    )
    static let warning = adaptive(
        light: UIColor(red: 0.522, green: 0.314, blue: 0.0, alpha: 1),
        dark: UIColor(red: 1.0, green: 0.72, blue: 0.20, alpha: 1)
    )
    static let danger = adaptive(
        light: UIColor(red: 0.76, green: 0.10, blue: 0.14, alpha: 1),
        dark: UIColor(red: 1.0, green: 0.36, blue: 0.39, alpha: 1)
    )
    static let shadow = adaptive(
        light: UIColor.black.withAlphaComponent(0.94),
        dark: UIColor.black.withAlphaComponent(0.92)
    )
    static let shadowSoft = adaptive(
        light: UIColor.black.withAlphaComponent(0.42),
        dark: UIColor.black.withAlphaComponent(0.62)
    )
    static let hardShadow = adaptive(
        light: UIColor.black.withAlphaComponent(0.80),
        dark: UIColor.black.withAlphaComponent(0.78)
    )
    static let glassTint = adaptive(
        light: UIColor(red: 1.0, green: 0.92, blue: 0.76, alpha: 0.28),
        dark: UIColor(red: 0.56, green: 0.20, blue: 0.035, alpha: 0.28)
    )
    static let glassSelectedTint = adaptive(
        light: UIColor(red: 1.0, green: 0.38, blue: 0.035, alpha: 0.36),
        dark: UIColor(red: 1.0, green: 0.42, blue: 0.08, alpha: 0.38)
    )
    static let glassOutline = adaptive(
        light: UIColor.black,
        dark: UIColor(red: 0.975, green: 0.949, blue: 0.894, alpha: 0.32)
    )
    static let glassShadow = adaptive(
        light: UIColor.black.withAlphaComponent(0.80),
        dark: UIColor.black.withAlphaComponent(0.50)
    )
    static let skeleton = adaptive(
        light: UIColor.black.withAlphaComponent(0.070),
        dark: UIColor.white.withAlphaComponent(0.055)
    )
    static let skeletonStrong = adaptive(
        light: UIColor.black.withAlphaComponent(0.12),
        dark: UIColor.white.withAlphaComponent(0.095)
    )

    static let panelRadius: CGFloat = 4
    static let controlRadius: CGFloat = 4
    static let iconRadius: CGFloat = 3

    static func displayFont(_ style: Font.TextStyle, weight: Font.Weight = .black) -> Font {
        .system(style, design: .default, weight: weight).width(.condensed)
    }
}

/// Shared responsive dimensions for the app's operator workspace. Compact
/// windows stay edge-to-edge; regular-width windows gain useful density without
/// letting controls or long-form text stretch across the entire display.
enum AppLayout {
    static let formMaxWidth: CGFloat = 620
    static let detailMaxWidth: CGFloat = 920
    static let catalogMaxWidth: CGFloat = 1080
    static let dashboardMaxWidth: CGFloat = 1180

    static func pagePadding(for sizeClass: UserInterfaceSizeClass?) -> CGFloat {
        sizeClass == .regular ? 24 : 16
    }

    static func adaptiveColumns(
        for sizeClass: UserInterfaceSizeClass?,
        regularMinimum: CGFloat,
        regularMaximum: CGFloat = .infinity,
        spacing: CGFloat = 16
    ) -> [GridItem] {
        guard sizeClass == .regular else {
            return [GridItem(.flexible())]
        }
        return [
            GridItem(
                .adaptive(minimum: regularMinimum, maximum: regularMaximum),
                spacing: spacing,
                alignment: .top
            )
        ]
    }
}

/// Keeps compact empty states vertically centered while making their complete
/// message and action scrollable at accessibility text sizes.
struct AppAdaptiveEmptyStateContainer<Content: View>: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private let maxWidth: CGFloat
    private let content: Content

    init(
        maxWidth: CGFloat = 560,
        @ViewBuilder content: () -> Content
    ) {
        self.maxWidth = maxWidth
        self.content = content()
    }

    @ViewBuilder
    var body: some View {
        if dynamicTypeSize.isAccessibilitySize {
            ScrollView {
                content
                    .padding(.horizontal, 16)
                    .padding(.vertical, 24)
                    .frame(maxWidth: maxWidth)
                    .frame(maxWidth: .infinity)
            }
            .scrollBounceBehavior(.basedOnSize)
            .scrollIndicators(.hidden)
        } else {
            content
                .padding(.horizontal, 16)
                .frame(maxWidth: maxWidth)
        }
    }
}

struct AppAdaptiveTwoPane<Primary: View, Secondary: View>: View {
    private let primary: Primary
    private let secondary: Secondary
    private let primaryMinimumWidth: CGFloat
    private let secondaryMinimumWidth: CGFloat
    private let spacing: CGFloat

    init(
        primaryMinimumWidth: CGFloat = 400,
        secondaryMinimumWidth: CGFloat = 320,
        spacing: CGFloat = 16,
        @ViewBuilder primary: () -> Primary,
        @ViewBuilder secondary: () -> Secondary
    ) {
        self.primary = primary()
        self.secondary = secondary()
        self.primaryMinimumWidth = primaryMinimumWidth
        self.secondaryMinimumWidth = secondaryMinimumWidth
        self.spacing = spacing
    }

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .top, spacing: spacing) {
                primary
                    .frame(minWidth: primaryMinimumWidth, maxWidth: .infinity, alignment: .top)
                secondary
                    .frame(minWidth: secondaryMinimumWidth, maxWidth: .infinity, alignment: .top)
            }
            VStack(spacing: spacing) {
                primary
                secondary
            }
        }
    }
}

struct AppSurfaceModifier: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme

    var cornerRadius: CGFloat = AppTheme.panelRadius
    var raised = false

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        content
            .background(raised ? AppTheme.surfaceRaised : AppTheme.surface)
            .clipShape(shape)
            .overlay {
                shape.strokeBorder(
                    raised ? AppTheme.strokeStrong : AppTheme.stroke,
                    lineWidth: colorScheme == .dark
                        ? (raised ? 1.5 : 1.15)
                        : (raised ? 2 : 1.75)
                )
            }
            .background {
                shape
                    .fill(raised ? AppTheme.shadow : AppTheme.hardShadow)
                    .offset(
                        x: colorScheme == .dark ? (raised ? 3 : 2) : (raised ? 4 : 3),
                        y: colorScheme == .dark ? (raised ? 3 : 2) : (raised ? 4 : 3)
                    )
            }
    }
}

struct ProviderSurfaceModifier: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme

    let accent: Color
    var cornerRadius: CGFloat = AppTheme.panelRadius

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        content
            .background {
                LinearGradient(
                    colors: [accent.opacity(0.13), AppTheme.surface, AppTheme.surface],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            }
            .clipShape(shape)
            .overlay {
                shape.strokeBorder(
                    AppTheme.strokeStrong,
                    lineWidth: colorScheme == .dark ? 1.5 : 2
                )
            }
            .overlay(alignment: .leading) {
                Rectangle()
                    .fill(accent)
                    .frame(width: 4)
            }
            .background {
                shape
                    .fill(AppTheme.hardShadow)
                    .offset(
                        x: colorScheme == .dark ? 3 : 4,
                        y: colorScheme == .dark ? 3 : 4
                    )
            }
    }
}

struct NativeGlassSurfaceModifier: ViewModifier {
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.colorScheme) private var colorScheme

    let cornerRadius: CGFloat
    let isInteractive: Bool
    let tint: Color

    @ViewBuilder
    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        let brandedTint = tint.opacity(colorScheme == .dark ? 0.72 : 1)

        if #available(iOS 26.0, *) {
            if isInteractive {
                content
                    .glassEffect(
                        .regular.tint(brandedTint).interactive(),
                        in: .rect(cornerRadius: cornerRadius)
                    )
                    .background { brandedGlassShadow(shape).allowsHitTesting(false) }
                    .overlay { brandedGlassOutline(shape).allowsHitTesting(false) }
            } else {
                content
                    .glassEffect(
                        .regular.tint(brandedTint),
                        in: .rect(cornerRadius: cornerRadius)
                    )
                    .background { brandedGlassShadow(shape).allowsHitTesting(false) }
                    .overlay { brandedGlassOutline(shape).allowsHitTesting(false) }
            }
        } else {
            content
                .background {
                    if reduceTransparency {
                        shape.fill(AppTheme.surface)
                    } else {
                        shape.fill(.ultraThinMaterial)
                        shape.fill(AppTheme.canvas.opacity(0.38))
                    }
                    shape.fill(brandedTint)
                }
                .clipShape(shape)
                .background { brandedGlassShadow(shape).allowsHitTesting(false) }
                .overlay { brandedGlassOutline(shape).allowsHitTesting(false) }
        }
    }

    private func brandedGlassOutline(_ shape: RoundedRectangle) -> some View {
        shape.strokeBorder(
            AppTheme.glassOutline,
            lineWidth: colorScheme == .dark ? 1.25 : 1.75
        )
    }

    private func brandedGlassShadow(_ shape: RoundedRectangle) -> some View {
        shape
            .strokeBorder(
                AppTheme.glassShadow,
                lineWidth: colorScheme == .dark ? 2 : 3
            )
            .offset(
                x: colorScheme == .dark ? 2 : 3,
                y: colorScheme == .dark ? 3 : 4
            )
    }
}

/// A branded toolbar item whose Liquid Glass surface remains owned by SwiftUI's
/// native toolbar. Its label supplies only the app's icon treatment and sizing.
struct AppThemedToolbarItem<Content: View>: ToolbarContent {
    let placement: ToolbarItemPlacement
    private let content: Content

    init(
        placement: ToolbarItemPlacement,
        @ViewBuilder content: () -> Content
    ) {
        self.placement = placement
        self.content = content()
    }

    var body: some ToolbarContent {
        ToolbarItem(placement: placement) {
            content
        }
    }
}

extension View {
    func appSurface(cornerRadius: CGFloat = AppTheme.panelRadius, raised: Bool = false) -> some View {
        modifier(AppSurfaceModifier(cornerRadius: cornerRadius, raised: raised))
    }

    func providerSurface(accent: Color, cornerRadius: CGFloat = AppTheme.panelRadius) -> some View {
        modifier(ProviderSurfaceModifier(accent: accent, cornerRadius: cornerRadius))
    }

    func nativeGlassSurface(
        cornerRadius: CGFloat,
        isInteractive: Bool = false,
        tint: Color = AppTheme.glassTint
    ) -> some View {
        modifier(
            NativeGlassSurfaceModifier(
                cornerRadius: cornerRadius,
                isInteractive: isInteractive,
                tint: tint
            )
        )
    }

    func appContentWidth(
        _ width: CGFloat,
        horizontalSizeClass: UserInterfaceSizeClass?
    ) -> some View {
        frame(maxWidth: horizontalSizeClass == .regular ? width : .infinity)
            .frame(maxWidth: .infinity)
    }

    /// Applies the Verceltics palette and compact-corner language to native
    /// buttons, menus, pickers, toolbars, and other system control chrome. The
    /// controls continue to use their platform-provided materials and effects.
    func appNativeControlTheme() -> some View {
        tint(AppTheme.navigationAccent)
            .buttonBorderShape(.roundedRectangle(radius: AppTheme.controlRadius))
    }
}

/// A themed search field that remains real Liquid Glass on iOS 26. The ink
/// outline belongs to the custom surface; system navigation glass is left to
/// SwiftUI so it can continue to refract and animate natively.
struct AppGlassSearchField: View {
    @Binding private var text: String
    @FocusState private var isFocused: Bool

    let prompt: String
    let accent: Color
    let startsFocused: Bool
    let focusRequestID: Int

    init(
        text: Binding<String>,
        prompt: String,
        accent: Color = AppTheme.navigationAccent,
        startsFocused: Bool = false,
        focusRequestID: Int = 0
    ) {
        _text = text
        self.prompt = prompt
        self.accent = accent
        self.startsFocused = startsFocused
        self.focusRequestID = focusRequestID
    }

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 20, weight: .black))
                .foregroundStyle(accent)
                .accessibilityHidden(true)

            TextField(prompt, text: $text)
                .font(.body)
                .foregroundStyle(AppTheme.textPrimary)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .focused($isFocused)
                .onSubmit { isFocused = false }
                .accessibilityLabel(prompt)
                .accessibilityIdentifier("workspaceSearch.input")

            if !text.isEmpty {
                Button {
                    text = ""
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 11, weight: .black))
                        .foregroundStyle(AppTheme.signalForeground)
                        .frame(width: 28, height: 28)
                        .background(accent)
                        .clipShape(RoundedRectangle(cornerRadius: 2, style: .continuous))
                        .overlay {
                            RoundedRectangle(cornerRadius: 2, style: .continuous)
                                .strokeBorder(AppTheme.strokeStrong, lineWidth: 1)
                        }
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Clear search")
                .accessibilityIdentifier("workspaceSearch.clear")
            }
        }
        .padding(.leading, 16)
        .padding(.trailing, text.isEmpty ? 16 : 4)
        .frame(minHeight: 54)
        .nativeGlassSurface(
            cornerRadius: AppTheme.controlRadius,
            isInteractive: true,
            tint: accent.opacity(0.10)
        )
        .onAppear {
            guard startsFocused else { return }
            focusAfterLayout()
        }
        .onChange(of: focusRequestID) {
            focusAfterLayout()
        }
    }

    private func focusAfterLayout() {
        Task { @MainActor in
            await Task.yield()
            isFocused = true
        }
    }
}

/// Branded label content for a native Liquid Glass toolbar control. The label
/// intentionally draws no material so the toolbar supplies exactly one glass
/// surface around it.
struct AppToolbarActionLabel: View {
    let systemImage: String
    var accent: Color = AppTheme.navigationAccent
    var rotation: Double = 0
    var isBusy = false

    var body: some View {
        Group {
            if isBusy {
                ProgressView()
                    .controlSize(.small)
                    .tint(accent)
            } else {
                VStack(spacing: 3) {
                    Image(systemName: systemImage)
                        .font(.system(size: 16, weight: .black))
                        .symbolRenderingMode(.monochrome)
                        .rotationEffect(.degrees(rotation))

                    Rectangle()
                        .fill(accent)
                        .frame(width: 18, height: 2)
                }
                .foregroundStyle(AppTheme.textPrimary)
            }
        }
        .frame(width: 44, height: 44)
        .contentShape(Rectangle())
    }
}

enum AppStatusTone {
    case success
    case warning
    case danger
    case progress
    case neutral

    var color: Color {
        switch self {
        case .success: AppTheme.success
        case .warning: AppTheme.warning
        case .danger: AppTheme.danger
        case .progress: AppTheme.signal
        case .neutral: AppTheme.textSecondary
        }
    }

    static func status(_ value: String) -> AppStatusTone {
        let value = value.lowercased()
        if value.contains("inactive") || value.contains("deactiv") || value.contains("expired")
            || value.contains("disabled") || value.contains("deleted") || value.contains("blocked")
            || value.contains("fail") || value.contains("error") || value.contains("cancel")
            || value.contains("suspend") || value.contains("fatal") || value.contains("stopped")
            || value.contains("offline") {
            return .danger
        }
        if value.contains("build") || value.contains("progress") || value.contains("initial") {
            return .progress
        }
        if value.contains("pending") || value.contains("queued") || value.contains("starting")
            || value.contains("warning") || value.contains("paused") || value.contains("not ready")
            || value.contains("incomplete") {
            return .warning
        }
        if value.contains("active") || value.contains("ready") || value.contains("success")
            || value.contains("live") || value.contains("running") || value.contains("published")
            || value.contains("succeed") || value.contains("complete") {
            return .success
        }
        return .neutral
    }
}

struct AppStatusBadge: View {
    let text: String
    var tone: AppStatusTone = .neutral

    var body: some View {
        HStack(spacing: 5) {
            Circle()
                .fill(tone.color)
                .frame(width: 6, height: 6)
            Text(text)
                .font(AppTheme.displayFont(.caption2))
                .textCase(.uppercase)
                .lineLimit(1)
        }
            .foregroundStyle(AppTheme.textPrimary)
            .padding(.horizontal, 9)
            .padding(.vertical, 6)
            .background(
                tone.color.opacity(0.16),
                in: RoundedRectangle(cornerRadius: 4, style: .continuous)
            )
            .overlay {
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .strokeBorder(tone.color, lineWidth: 1.25)
            }
            .accessibilityLabel("Status: \(text)")
    }
}

struct AppIconTile: View {
    let icon: String
    var tint: Color = AppTheme.signalFill
    var size: CGFloat = 36

    var body: some View {
        Image(systemName: icon)
            .font(.system(size: size * 0.38, weight: .bold))
            .foregroundStyle(AppTheme.textPrimary)
            .frame(width: size, height: size)
            .background(tint.opacity(0.16))
            .clipShape(RoundedRectangle(cornerRadius: AppTheme.iconRadius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: AppTheme.iconRadius, style: .continuous)
                    .strokeBorder(tint, lineWidth: 1.25)
            }
            .accessibilityHidden(true)
    }
}

struct AppSectionHeader: View {
    let title: String
    var count: Int?
    var accent: Color = AppTheme.textSecondary

    var body: some View {
        HStack(spacing: 8) {
            Text(title.uppercased())
                .font(AppTheme.displayFont(.caption))
                .tracking(0.9)
                .foregroundStyle(AppTheme.textPrimary)
            Spacer(minLength: 8)
            if let count {
                Text(count.formatted())
                    .font(AppTheme.displayFont(.caption).monospacedDigit())
                    .foregroundStyle(AppTheme.textPrimary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(accent.opacity(0.16), in: RoundedRectangle(cornerRadius: 3, style: .continuous))
                    .overlay {
                        RoundedRectangle(cornerRadius: 3, style: .continuous)
                            .strokeBorder(accent, lineWidth: 1)
                    }
            }
        }
    }
}

struct AppFeedbackBanner: View {
    let title: String
    let message: String
    var icon = "exclamationmark.triangle.fill"
    var tint = AppTheme.warning
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            AppIconTile(icon: icon, tint: tint, size: 34)
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(AppTheme.textPrimary)
                Text(message)
                    .font(.footnote)
                    .foregroundStyle(AppTheme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                if let actionTitle, let action {
                    Button(actionTitle, action: action)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(tint)
                        .frame(minHeight: 44, alignment: .leading)
                        .contentShape(Rectangle())
                        .buttonStyle(.plain)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(15)
        .providerSurface(accent: tint)
    }
}

struct AppEmptyState: View {
    let icon: String
    let title: String
    let message: String
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        VStack(spacing: 16) {
            AppIconTile(icon: icon, size: 50)
            VStack(spacing: 6) {
                Text(title)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(AppTheme.textPrimary)
                Text(message)
                    .font(.footnote)
                    .foregroundStyle(AppTheme.textSecondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(AppTheme.signalForeground)
                    .padding(.horizontal, 18)
                    .frame(minHeight: 44)
                    .background(AppTheme.signalFill, in: RoundedRectangle(cornerRadius: AppTheme.controlRadius, style: .continuous))
                    .buttonStyle(PressScaleButtonStyle())
            }
        }
        .frame(maxWidth: 380)
        .padding(28)
    }
}

/// Shared loading composition for hosting and registrar dashboards. It keeps
/// the final page geometry visible, so switching accounts feels stable rather
/// than replacing the whole workspace with a spinner.
struct AppDashboardLoadingView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    var accent: Color = AppTheme.signalFill
    var showsMetrics = true

    private var columns: [GridItem] {
        AppLayout.adaptiveColumns(
            for: horizontalSizeClass,
            regularMinimum: 340,
            regularMaximum: 540,
            spacing: 14
        )
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                skeletonBlock(height: 92, accent: true)

                if showsMetrics {
                    LazyVGrid(
                        columns: horizontalSizeClass == .regular
                            ? Array(repeating: GridItem(.flexible(), spacing: 10), count: 3)
                            : [GridItem(.adaptive(minimum: 96), spacing: 10)],
                        spacing: 10
                    ) {
                        ForEach(0..<3, id: \.self) { _ in
                            skeletonBlock(height: 84)
                        }
                    }
                }

                HStack(spacing: 10) {
                    skeletonBlock(height: 48)
                    skeletonBlock(height: 48)
                }

                HStack {
                    RoundedRectangle(cornerRadius: 3, style: .continuous)
                        .fill(AppTheme.skeletonStrong)
                        .frame(width: 118, height: 10)
                    Spacer()
                }

                LazyVGrid(columns: columns, spacing: 14) {
                    ForEach(0..<6, id: \.self) { _ in
                        skeletonBlock(height: 74)
                    }
                }
            }
            .padding(.horizontal, AppLayout.pagePadding(for: horizontalSizeClass))
            .padding(.top, 18)
            .padding(.bottom, 24)
            .appContentWidth(AppLayout.dashboardMaxWidth, horizontalSizeClass: horizontalSizeClass)
            .shimmering()
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Loading dashboard")
    }

    private func skeletonBlock(height: CGFloat, accent: Bool = false) -> some View {
        RoundedRectangle(cornerRadius: AppTheme.panelRadius, style: .continuous)
            .fill(accent ? self.accent.opacity(0.07) : AppTheme.skeleton)
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .overlay {
                RoundedRectangle(cornerRadius: AppTheme.panelRadius, style: .continuous)
                    .strokeBorder(accent ? self.accent.opacity(0.12) : AppTheme.strokeSoft, lineWidth: 0.5)
            }
    }
}

struct AppInsetDivider: View {
    var leading: CGFloat = 62

    var body: some View {
        Rectangle()
            .fill(AppTheme.divider)
            .frame(height: 0.5)
            .padding(.leading, leading)
    }
}

struct AppAPIResponsePane: View {
    let statusCode: Int
    let headers: [String: String]
    let responseBody: String

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var selection: ResponseSection = .body

    private enum ResponseSection: String, CaseIterable, Identifiable {
        case body = "Body"
        case headers = "Headers"
        var id: Self { self }
    }

    init(statusCode: Int, headers: [String: String], body: String) {
        self.statusCode = statusCode
        self.headers = headers
        responseBody = body
    }

    private var selectedText: String {
        switch selection {
        case .body: responseBody
        case .headers:
            headers
                .sorted { $0.key.localizedCaseInsensitiveCompare($1.key) == .orderedAscending }
                .map { "\($0.key): \($0.value)" }
                .joined(separator: "\n")
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Label("Response", systemImage: "terminal.fill")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(AppTheme.textPrimary)
                Spacer(minLength: 8)
                AppStatusBadge(
                    text: "HTTP \(statusCode)",
                    tone: (200...299).contains(statusCode) ? .success : .danger
                )
                Button {
                    UIPasteboard.general.string = selectedText
                } label: {
                    Image(systemName: "doc.on.doc")
                        .font(.subheadline.weight(.semibold))
                        .frame(width: 36, height: 36)
                        .contentShape(Rectangle())
                }
                .buttonStyle(PressScaleButtonStyle())
                .foregroundStyle(AppTheme.textSecondary)
                .accessibilityLabel("Copy \(selection.rawValue.lowercased())")
            }

            if !headers.isEmpty {
                Picker("Response section", selection: $selection) {
                    ForEach(ResponseSection.allCases) { section in
                        Text(section.rawValue).tag(section)
                    }
                }
                .pickerStyle(.segmented)
            }

            ScrollView([.horizontal, .vertical]) {
                Text(selectedText.isEmpty ? "No response content" : selectedText)
                    .font(.footnote.monospaced())
                    .foregroundStyle(selectedText.isEmpty ? AppTheme.textTertiary : AppTheme.textPrimary)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .topLeading)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(12)
            }
            .frame(
                minHeight: 190,
                maxHeight: horizontalSizeClass == .regular ? 460 : 320
            )
            .background(AppTheme.canvas.opacity(0.72), in: RoundedRectangle(cornerRadius: AppTheme.controlRadius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: AppTheme.controlRadius, style: .continuous)
                    .strokeBorder(AppTheme.strokeSoft, lineWidth: 0.5)
            }
        }
        .padding(15)
        .appSurface()
    }
}

extension AccountProvider {
    var logoAssetName: String {
        switch self {
        case .vercel: "VercelMark"
        case .cloudflare: "CloudflareMark"
        case .netlify: "NetlifyMark"
        case .railway: "RailwayMark"
        case .render: "RenderMark"
        case .digitalOcean: "DigitalOceanMark"
        case .heroku: "HerokuMark"
        case .fly: "FlyMark"
        case .firebase: "FirebaseMark"
        case .awsAmplify: "AWSAmplifyMark"
        }
    }

    var logoNeedsTint: Bool {
        switch self {
        case .vercel, .railway, .render, .heroku, .fly: true
        case .cloudflare, .netlify, .digitalOcean, .firebase, .awsAmplify: false
        }
    }

    var systemImage: String {
        switch self {
        case .vercel: "triangle.fill"
        case .cloudflare: "cloud.fill"
        case .netlify: "bolt.horizontal.fill"
        case .railway: "tram.fill"
        case .render: "square.3.layers.3d"
        case .digitalOcean: "drop.fill"
        case .heroku: "h.square.fill"
        case .fly: "airplane"
        case .firebase: "flame.fill"
        case .awsAmplify: "cloud.fill"
        }
    }

    var accentColor: Color {
        switch self {
        case .vercel: AppTheme.textPrimary
        case .cloudflare: Color(red: 0.95, green: 0.42, blue: 0.08)
        case .netlify: Color(red: 0.18, green: 0.82, blue: 0.78)
        case .railway: Color(red: 0.67, green: 0.48, blue: 0.98)
        case .render: Color(red: 0.38, green: 0.47, blue: 1.0)
        case .digitalOcean: Color(red: 0.0, green: 0.46, blue: 0.95)
        case .heroku: Color(red: 0.55, green: 0.34, blue: 0.84)
        case .fly: Color(red: 0.53, green: 0.64, blue: 1.0)
        case .firebase: Color(red: 1.0, green: 0.68, blue: 0.12)
        case .awsAmplify: Color(red: 1.0, green: 0.60, blue: 0.12)
        }
    }

    var connectionSubtitle: String {
        switch self {
        case .vercel: "Projects, deployments and Web Analytics"
        case .cloudflare: "Zones, Pages, Workers, DNS and analytics"
        case .netlify: "Sites, deploys, domains and build controls"
        case .railway: "Projects, services, environments and logs"
        case .render: "Services, deploys, jobs and environments"
        case .digitalOcean: "Apps, deployments, logs and bandwidth"
        case .heroku: "Apps, releases, dynos, domains and logs"
        case .fly: "Apps, Machines, regions and volumes"
        case .firebase: "Hosting sites, channels, versions and releases"
        case .awsAmplify: "Apps, branches, jobs and domains"
        }
    }

    var credentialPageURL: URL? {
        let value: String
        switch self {
        case .vercel: value = "https://vercel.com/account/tokens"
        case .cloudflare: value = "https://dash.cloudflare.com/profile/api-tokens"
        case .netlify: value = "https://app.netlify.com/user/applications#personal-access-tokens"
        case .railway: value = "https://railway.com/account/tokens"
        case .render: value = "https://dashboard.render.com/u/settings#api-keys"
        case .digitalOcean: value = "https://cloud.digitalocean.com/account/api/tokens"
        case .heroku: value = "https://dashboard.heroku.com/account/applications"
        case .fly: value = "https://fly.io/user/personal_access_tokens"
        case .firebase: value = "https://console.firebase.google.com/"
        case .awsAmplify: value = "https://console.aws.amazon.com/iam/home#/security_credentials"
        }
        return URL(string: value)
    }

    var primaryActionLabel: String? {
        switch self {
        case .netlify, .render, .digitalOcean, .railway: "Redeploy"
        case .heroku, .fly: "Restart"
        case .awsAmplify: "Start release"
        case .vercel, .cloudflare, .firebase: nil
        }
    }
}

struct ProviderMark: View {
    let provider: AccountProvider
    var size: CGFloat = 22
    var monochrome = false

    var body: some View {
        Image(provider.logoAssetName)
            .resizable()
            .renderingMode(monochrome || provider.logoNeedsTint ? .template : .original)
            .scaledToFit()
            .foregroundStyle(monochrome ? AppTheme.textPrimary : provider.accentColor)
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}
