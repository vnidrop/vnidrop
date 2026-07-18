import Foundation

/// Top-level destinations, ported from `ui/navigation/AppDestination.kt`.
enum AppDestination: String, CaseIterable, Identifiable {
	case send
	case receive
	case settings

	var id: String { rawValue }

	var labelKey: String {
		switch self {
		case .send: return "nav_send"
		case .receive: return "nav_receive"
		case .settings: return "nav_settings"
		}
	}

	/// SF Symbol approximating the Compose line icon.
	var systemImage: String {
		switch self {
		case .send: return "paperplane"
		case .receive: return "tray.and.arrow.down"
		case .settings: return "gearshape"
		}
	}
}
