import SwiftUI

/// Cross-platform decoding of raw image bytes into a SwiftUI `Image`.
enum PlatformImage {
	static func from(data: Data) -> Image? {
		#if os(iOS)
		guard let ui = UIImage(data: data) else { return nil }
		return Image(uiImage: ui)
		#else
		guard let ns = NSImage(data: data) else { return nil }
		return Image(nsImage: ns)
		#endif
	}
}

#if os(iOS)
import UIKit
#else
import AppKit
#endif
