import Shared
import SwiftUI

@main
struct iOSApp: App {
    private let externalInvitations = ExternalInvitationController()

    var body: some Scene {
        WindowGroup {
            ContentView(externalInvitations: externalInvitations)
        }
    }
}
