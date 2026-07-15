package com.vnidrop.app.diagnostics

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun platformHttpPost(
	url: String,
	headers: Map<String, String>,
	bodyUtf8: String,
): PlatformHttpResponse = withContext(Dispatchers.IO) {
	val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
		requestMethod = "POST"
		doOutput = true
		connectTimeout = 15_000
		readTimeout = 30_000
		setRequestProperty("Content-Type", "application/json; charset=utf-8")
		headers.forEach { (key, value) -> setRequestProperty(key, value) }
	}
	try {
		connection.outputStream.use { output ->
			output.write(bodyUtf8.toByteArray(StandardCharsets.UTF_8))
		}
		val code = connection.responseCode
		val stream = if (code in 200..299) connection.inputStream else connection.errorStream
		val body = stream?.use { input ->
			BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
		}.orEmpty()
		PlatformHttpResponse(code, body)
	} finally {
		connection.disconnect()
	}
}
