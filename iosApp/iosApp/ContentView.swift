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
    func makeUIViewController(context: Self.Context) -> UIViewController {
        VniDropHostViewController(composeController: MainViewControllerKt.MainViewController())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
