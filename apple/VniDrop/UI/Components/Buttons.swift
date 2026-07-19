import SwiftUI

/// Native SwiftUI button styles. Purple accent comes from the app-wide `.tint`.

/// Full-width filled button (`.borderedProminent`). Apply `.fixedSize()` at the
/// call site to shrink it to its content.
struct PrimaryButton: View {
	let title: String
	let action: () -> Void
	var enabled: Bool = true

	var body: some View {
		Button(action: action) {
			Text(title).frame(maxWidth: .infinity).frame(minHeight: 22)
		}
		.buttonStyle(.borderedProminent)
		.controlSize(.large)
		.disabled(!enabled)
	}
}

/// Full-width bordered (secondary) button.
struct SecondaryButton: View {
	let title: String
	let action: () -> Void
	var enabled: Bool = true

	var body: some View {
		Button(action: action) {
			Text(title).frame(maxWidth: .infinity).frame(minHeight: 22)
		}
		.buttonStyle(.bordered)
		.controlSize(.large)
		.disabled(!enabled)
	}
}

/// Borderless tinted text button.
struct QuietButton: View {
	let title: String
	let action: () -> Void
	var enabled: Bool = true

	var body: some View {
		Button(title, action: action)
			.buttonStyle(.borderless)
			.disabled(!enabled)
	}
}

