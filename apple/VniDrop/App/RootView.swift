import SwiftUI

/// App root, ported from `App.kt`. Owns the object graph and feature models, wires
/// the adaptive shell, floating actions, snackbar host, and approval modal.
struct RootView: View {
	@StateObject private var graph: AppGraph
	@StateObject private var appModel: AppModel
	@StateObject private var sendModel: SendModel
	@StateObject private var receiveModel: ReceiveModel
	@StateObject private var settingsModel: SettingsModel
	@ObservedObject private var messages: UiMessageController
	@ObservedObject private var approvals: ApprovalCoordinator

	@Environment(\.scenePhase) private var scenePhase

	init(dependencies: AppDependencies) {
		let graph = AppGraph(dependencies: dependencies)
		_graph = StateObject(wrappedValue: graph)
		_appModel = StateObject(wrappedValue: AppModel(
			environment: dependencies.environment,
			repository: graph.coreRepository,
			preferences: graph.preferencesRepository,
			messages: graph.messages
		))
		_sendModel = StateObject(wrappedValue: SendModel(
			repository: graph.coreRepository,
			fileSystemService: dependencies.fileSystemService,
			preferences: graph.preferencesRepository,
			filePreviewRepository: graph.filePreviewRepository,
			messages: graph.messages
		))
		_receiveModel = StateObject(wrappedValue: ReceiveModel(
			repository: graph.coreRepository,
			fileSystemService: dependencies.fileSystemService,
			preferences: graph.preferencesRepository,
			messages: graph.messages
		))
		_settingsModel = StateObject(wrappedValue: SettingsModel(
			environment: dependencies.environment,
			deviceInfoProvider: dependencies.deviceInfoProvider,
			fileSystemService: dependencies.fileSystemService,
			preferences: graph.preferencesRepository,
			notifications: dependencies.notificationService,
			messages: graph.messages,
			bugReports: NoopBugReportService()
		))
		messages = graph.messages
		approvals = graph.approvalCoordinator
	}

	var body: some View {
		GeometryReader { proxy in
			let windowClass = windowClassFor(width: proxy.size.width)
			let isDark = resolveDarkTheme(appModel.themeMode, systemDark: systemDark)
			ZStack {
				navigation(windowClass: windowClass)
				SnackbarHost(controller: messages)
				ApprovalModalHost(
					state: approvals.state,
					onAccept: approvals.accept,
					onRefuse: approvals.refuse
				)
			}
			.vniDropTheme(isDark: isDark)
			.preferredColorScheme(appModel.themeMode.preferredColorScheme)
			.environment(\.vniColors, isDark ? .dark : .light)
		}
		.platformPickers(settingsModel: settingsModel)
		.task { await consumeExternalInvitations() }
		.onChange(of: scenePhase) { phase in
			switch phase {
			case .active:
				graph.visibility.setForeground(true)
				settingsModel.refreshNotificationPermission()
				// Reconcile against the durable snapshot: while the window was
				// unfocused/occluded (common on macOS) live events may not have
				// rendered, leaving progress/status stale.
				Task { _ = await graph.coreRepository.refresh() }
			case .background, .inactive:
				graph.visibility.setForeground(false)
			@unknown default:
				break
			}
		}
	}

	/// iOS uses a bottom tab bar; macOS uses a native source-list sidebar so each
	/// screen's toolbar lives in the detail column instead of the shared title bar.
	@ViewBuilder
	private func navigation(windowClass: WindowClass) -> some View {
		#if os(macOS)
		NavigationSplitView {
			List(AppDestination.allCases, selection: sidebarBinding) { destination in
				Label(LocalizedStringKey(destination.labelKey), systemImage: destination.systemImage)
					.tag(destination)
			}
			.navigationSplitViewColumnWidth(min: 180, ideal: 200, max: 260)
		} detail: {
			screen(for: appModel.destination, windowClass: windowClass)
		}
		#else
		TabView(selection: destinationBinding) {
			ForEach(AppDestination.allCases) { destination in
				screen(for: destination, windowClass: windowClass)
					.tabItem {
						Label(LocalizedStringKey(destination.labelKey), systemImage: destination.systemImage)
					}
					.tag(destination)
			}
		}
		#endif
	}

	private var sidebarBinding: Binding<AppDestination?> {
		Binding(
			get: { appModel.destination },
			set: { newValue in
				if let value = newValue {
					Task { @MainActor in appModel.selectDestination(value) }
				}
			}
		)
	}

	private var destinationBinding: Binding<AppDestination> {
		// Defer the write out of the current view-update cycle: TabView reconciles
		// its selection synchronously during body evaluation on macOS, and mutating
		// the published `destination` there triggers a "publishing within view
		// updates" warning.
		Binding(get: { appModel.destination }, set: { newValue in
			Task { @MainActor in appModel.selectDestination(newValue) }
		})
	}

	@ViewBuilder
	private func screen(for destination: AppDestination, windowClass: WindowClass) -> some View {
		switch destination {
		case .send: SendScreen(model: sendModel, windowClass: windowClass)
		case .receive: ReceiveScreen(model: receiveModel, windowClass: windowClass)
		case .settings: SettingsScreen(model: settingsModel, windowClass: windowClass)
		}
	}

	private var systemDark: Bool {
		#if os(iOS)
		return UITraitCollection.current.userInterfaceStyle == .dark
		#else
		return NSApp.effectiveAppearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
		#endif
	}

	private func consumeExternalInvitations() async {
		for await invitation in graph.dependencies.externalInvitations.invitations {
			appModel.selectDestination(.receive)
			switch invitation {
			case .success(let raw):
				receiveModel.onInvitationResult(.invitationFile, .success(raw))
			case .failure(let error):
				receiveModel.onInvitationResult(.invitationFile, .failure(error))
			}
		}
	}
}

#if os(iOS)
import UIKit
#else
import AppKit
#endif
