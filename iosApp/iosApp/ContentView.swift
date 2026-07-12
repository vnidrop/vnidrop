import Shared
import SwiftUI
import UIKit

// Compose renders the app content, but UIKit owns the status bar style. This
// host listens for theme changes from shared Kotlin code and asks iOS to
// recompute the status bar contrast.
final class VniDropHostViewController: UIViewController {
    private let composeController: UIViewController
    private var usesDarkTheme: Bool

    init(composeController: UIViewController) {
        self.composeController = composeController
        self.usesDarkTheme = UITraitCollection.current.userInterfaceStyle == .dark
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override var preferredStatusBarStyle: UIStatusBarStyle {
        usesDarkTheme ? .lightContent : .darkContent
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        addChild(composeController)
        view.addSubview(composeController.view)
        composeController.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            composeController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            composeController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            composeController.view.topAnchor.constraint(equalTo: view.topAnchor),
            composeController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        composeController.didMove(toParent: self)

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(themeDidChange(_:)),
            name: Notification.Name("VniDropThemeChanged"),
            object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    @objc private func themeDidChange(_ notification: Notification) {
        guard let isDark = notification.userInfo?["isDark"] as? String else { return }
        usesDarkTheme = isDark == "true"
        setNeedsStatusBarAppearanceUpdate()
    }
}

struct ComposeView: UIViewControllerRepresentable {
    let externalInvitations: ExternalInvitationController

    func makeUIViewController(context: Self.Context) -> UIViewController {
        VniDropHostViewController(
            composeController: MainViewControllerKt.MainViewController(
                externalInvitations: externalInvitations
            )
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    let externalInvitations: ExternalInvitationController

    var body: some View {
        ComposeView(externalInvitations: externalInvitations)
            .ignoresSafeArea()
            .onOpenURL(perform: openInvitation)
    }

    private func openInvitation(_ url: URL) {
        guard url.pathExtension.caseInsensitiveCompare("vnd") == .orderedSame else {
            externalInvitations.reportOpenFailure(message: "This is not a VniDrop invitation")
            return
        }

        let hasSecurityAccess = url.startAccessingSecurityScopedResource()
        defer {
            if hasSecurityAccess {
                url.stopAccessingSecurityScopedResource()
            }
        }

        do {
            let values = try url.resourceValues(forKeys: [.fileSizeKey])
            if let fileSize = values.fileSize, fileSize > 65_536 {
                throw InvitationOpenError.tooLarge
            }
            let data = try Data(contentsOf: url, options: .mappedIfSafe)
            guard data.count <= 65_536 else { throw InvitationOpenError.tooLarge }
            guard let raw = String(data: data, encoding: .utf8) else {
                throw InvitationOpenError.invalidEncoding
            }
            externalInvitations.openInvitation(raw: raw)
        } catch {
            externalInvitations.reportOpenFailure(
                message: (error as? LocalizedError)?.errorDescription ?? "The invitation could not be opened"
            )
        }
    }
}

private enum InvitationOpenError: LocalizedError {
    case tooLarge
    case invalidEncoding

    var errorDescription: String? {
        switch self {
        case .tooLarge: "The invitation is too large"
        case .invalidEncoding: "The invitation is not valid text"
        }
    }
}
