import Foundation
import os

/// Minimal structured logger, ported from `logging/AppLogger.kt`. Never logs
/// tickets, endpoint ids, or file contents (callers pass only redacted fields).
enum AppLogger {
	private static let logger = Logger(subsystem: "com.vnidrop.app", category: "app")

	static func info(_ scope: String, _ message: String, _ fields: [String: String] = [:]) {
		logger.info("[\(scope, privacy: .public)] \(message, privacy: .public) \(fieldString(fields), privacy: .public)")
	}

	static func error(_ scope: String, _ message: String, _ error: Error? = nil) {
		if let error {
			logger.error("[\(scope, privacy: .public)] \(message, privacy: .public): \(error.technicalDetail, privacy: .public)")
		} else {
			logger.error("[\(scope, privacy: .public)] \(message, privacy: .public)")
		}
	}

	private static func fieldString(_ fields: [String: String]) -> String {
		fields.isEmpty ? "" : fields.map { "\($0)=\($1)" }.joined(separator: " ")
	}
}
