import SwiftUI

// MARK: - StatusPill

enum PillTone { case neutral, success, warning, destructive, brand }

struct StatusPill: View {
	let label: String
	var tone: PillTone = .neutral

	private var color: Color {
		switch tone {
		case .neutral: return .secondary
		case .success, .brand: return VniDropColors.brandPurple
		case .warning: return .orange
		case .destructive: return .red
		}
	}

	var body: some View {
		HStack(spacing: 5) {
			Circle().fill(color).frame(width: 6, height: 6)
			Text(label).font(.caption).fontWeight(.medium).foregroundStyle(color).lineLimit(1)
		}
		.padding(.horizontal, 9).padding(.vertical, 4)
		.background(color.opacity(0.14), in: Capsule())
	}
}

// MARK: - ProgressRow

struct ProgressRow: View {
	let labelKey: String
	let progress: Double?
	var detail: String? = nil

	var body: some View {
		VStack(alignment: .leading, spacing: 4) {
			HStack {
				Text(LocalizedStringKey(labelKey)).font(.subheadline).lineLimit(1)
				Spacer()
				if let progress {
					Text("\(Int(progress * 100))%").font(.caption).foregroundStyle(.secondary)
				}
			}
			if let detail {
				Text(detail).font(.caption).foregroundStyle(.secondary).lineLimit(1)
			}
			if let progress {
				ProgressView(value: progress)
			} else {
				ProgressView().progressViewStyle(.linear)
			}
		}
		.frame(maxWidth: .infinity)
	}
}

// MARK: - Field

/// Labeled text field using native styling. Renders cleanly both inside a `Form`
/// row and standalone (e.g. inside a sheet).
struct Field: View {
	let label: String
	@Binding var value: String
	var minLines: Int = 1
	var enabled: Bool = true

	var body: some View {
		VStack(alignment: .leading, spacing: 6) {
			Text(label).font(.subheadline).foregroundStyle(.secondary)
			Group {
				if minLines > 1 {
					TextField(label, text: $value, axis: .vertical)
						.lineLimit(minLines, reservesSpace: true)
				} else {
					TextField(label, text: $value)
				}
			}
			.textFieldStyle(.roundedBorder)
			.disabled(!enabled)
		}
	}
}

// MARK: - EmptyStateView

/// Native-styled empty state (iOS 16 compatible; avoids iOS 17
/// `ContentUnavailableView`).
struct EmptyStateView<Actions: View>: View {
	let systemImage: String
	let title: String
	let message: String
	@ViewBuilder var actions: () -> Actions

	var body: some View {
		VStack(spacing: 12) {
			Image(systemName: systemImage)
				.font(.system(size: 52))
				.foregroundStyle(.secondary)
			Text(title).font(.title2).fontWeight(.semibold).multilineTextAlignment(.center)
			Text(message)
				.font(.body).foregroundStyle(.secondary)
				.multilineTextAlignment(.center)
				.frame(maxWidth: 420)
			actions().padding(.top, 4)
		}
		.frame(maxWidth: .infinity)
		.padding(.horizontal, 24)
		.padding(.vertical, 48)
	}
}
