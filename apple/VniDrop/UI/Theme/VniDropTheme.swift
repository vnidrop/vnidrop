import SwiftUI

/// User-facing theme selection, mirrors `ThemeMode` in the Compose theme.
enum ThemeMode: String, CaseIterable, Codable, Sendable {
	case system
	case light
	case dark

	/// SwiftUI color-scheme override (`nil` follows the system).
	var preferredColorScheme: ColorScheme? {
		switch self {
		case .system: return nil
		case .light: return .light
		case .dark: return .dark
		}
	}
}

func resolveDarkTheme(_ mode: ThemeMode, systemDark: Bool) -> Bool {
	switch mode {
	case .system: return systemDark
	case .light: return false
	case .dark: return true
	}
}

private struct VniDropColorsKey: EnvironmentKey {
	static let defaultValue = VniDropColors.light
}

extension EnvironmentValues {
	/// Semantic VniDrop tokens for the active theme. Read with
	/// `@Environment(\.vniColors) private var colors`.
	var vniColors: VniDropColors {
		get { self[VniDropColorsKey.self] }
		set { self[VniDropColorsKey.self] = newValue }
	}
}

/// Provides the semantic token set matching the resolved light/dark theme to the
/// whole subtree. Apply once near the app root.
struct VniDropTheme: ViewModifier {
	let isDark: Bool

	func body(content: Content) -> some View {
		content
			.environment(\.vniColors, isDark ? .dark : .light)
			.tint(VniDropColors.brandPurple)
	}
}

extension View {
	func vniDropTheme(isDark: Bool) -> some View {
		modifier(VniDropTheme(isDark: isDark))
	}
}
