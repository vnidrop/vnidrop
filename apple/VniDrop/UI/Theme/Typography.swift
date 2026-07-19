import SwiftUI

/// Semantic type scale mapped from the Material typography styles the Compose UI
/// uses, so screens reference the same names during the port.
enum VniType {
	static let titleLarge = Font.system(size: 22, weight: .semibold)
	static let bodyLarge = Font.system(size: 16)
	static let bodyMedium = Font.system(size: 14)
	static let bodySmall = Font.system(size: 12)
	static let labelSmall = Font.system(size: 11, weight: .medium)
}
