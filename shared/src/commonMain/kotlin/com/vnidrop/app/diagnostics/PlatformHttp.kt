package com.vnidrop.app.diagnostics

data class PlatformHttpResponse(
	val statusCode: Int,
	val body: String,
)

expect suspend fun platformHttpPost(
	url: String,
	headers: Map<String, String>,
	bodyUtf8: String,
): PlatformHttpResponse
