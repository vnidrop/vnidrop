import Foundation
import Combine

/// Tracks whether the app is in the foreground, ported from `platform/AppVisibility.kt`.
@MainActor
final class AppVisibility: ObservableObject {
	@Published private(set) var isForeground: Bool = true

	func setForeground(_ value: Bool) {
		isForeground = value
	}
}
