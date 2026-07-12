package com.vnidrop.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.vnidrop.app.feature.receive.ExternalInvitationController
import com.vnidrop.app.feature.receive.MaxVniDropInvitationBytes
import com.vnidrop.app.feature.receive.VniDropInvitationExtension
import com.vnidrop.app.feature.receive.VniDropInvitationMimeType
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
	private val externalInvitations = ExternalInvitationController()

	override fun onCreate(savedInstanceState: Bundle?) {
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)
		setContent {
			App(rememberAndroidAppDependencies(this, externalInvitations))
		}
		handleInvitationIntent(intent)
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		handleInvitationIntent(intent)
	}

	private fun handleInvitationIntent(source: Intent?) {
		if (source?.action != Intent.ACTION_VIEW) return
		val uri = source.data
		setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
		if (uri == null) {
			externalInvitations.reportOpenFailure("The invitation could not be opened")
			return
		}
		lifecycleScope.launch {
			val result = withContext(Dispatchers.IO) { readInvitation(uri, source.type) }
			result.fold(externalInvitations::openInvitation) { error ->
				externalInvitations.reportOpenFailure(error.message ?: "The invitation could not be opened")
			}
		}
	}

	private fun readInvitation(uri: Uri, declaredType: String?): Result<String> = runCatching {
		val resolvedType = declaredType ?: contentResolver.getType(uri)
		val hasExpectedName = uri.lastPathSegment?.endsWith(".$VniDropInvitationExtension", ignoreCase = true) == true
		require(resolvedType == VniDropInvitationMimeType || hasExpectedName) { "This is not a VniDrop invitation" }
		val bytes = contentResolver.openInputStream(uri)?.use { it.readNBytes(MaxVniDropInvitationBytes + 1) }
			?: error("The invitation could not be opened")
		require(bytes.size <= MaxVniDropInvitationBytes) { "The invitation is too large" }
		Charsets.UTF_8.newDecoder()
			.onMalformedInput(CodingErrorAction.REPORT)
			.onUnmappableCharacter(CodingErrorAction.REPORT)
			.decode(ByteBuffer.wrap(bytes))
			.toString()
	}
}
