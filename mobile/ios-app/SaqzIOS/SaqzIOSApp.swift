import SwiftUI
import UIKit
import SaqzMobile

@main
struct SaqzIOSApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeRootView()
        }
    }
}

private struct ComposeRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController()
        let accessibilityText = UILabel(frame: CGRect(x: 0, y: 0, width: 1, height: 1))
        accessibilityText.accessibilityLabel = "Saqz"
        accessibilityText.isAccessibilityElement = true
        controller.view.addSubview(accessibilityText)
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
