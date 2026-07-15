package com.vnidrop.app.diagnostics

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.posix.memcpy
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
actual suspend fun platformHttpPost(
	url: String,
	headers: Map<String, String>,
	bodyUtf8: String,
): PlatformHttpResponse = suspendCancellableCoroutine { cont ->
	val nsUrl = NSURL.URLWithString(url)
	if (nsUrl == null) {
		cont.resume(PlatformHttpResponse(statusCode = 0, body = "invalid_url"))
		return@suspendCancellableCoroutine
	}
	val request = NSMutableURLRequest.requestWithURL(nsUrl).apply {
		setHTTPMethod("POST")
		setValue("application/json; charset=utf-8", forHTTPHeaderField = "Content-Type")
		headers.forEach { (key, value) ->
			setValue(value, forHTTPHeaderField = key)
		}
		setHTTPBody(bodyUtf8.encodeToByteArray().toNSData())
	}
	val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
		if (!cont.isActive) return@dataTaskWithRequest
		if (error != null) {
			val message = error.localizedDescription
			cont.resume(PlatformHttpResponse(statusCode = 0, body = message))
			return@dataTaskWithRequest
		}
		val http = response as? NSHTTPURLResponse
		val status = http?.statusCode?.toInt() ?: 0
		val body = data?.toUtf8String().orEmpty()
		cont.resume(PlatformHttpResponse(statusCode = status, body = body))
	}
	cont.invokeOnCancellation { task.cancel() }
	task.resume()
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData =
	usePinned { pinned ->
		NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
	}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toUtf8String(): String {
	val size = length.toInt()
	if (size == 0) return ""
	val result = ByteArray(size)
	val source = bytes ?: return ""
	result.usePinned { pinned ->
		memcpy(pinned.addressOf(0), source, size.convert())
	}
	return result.decodeToString()
}
