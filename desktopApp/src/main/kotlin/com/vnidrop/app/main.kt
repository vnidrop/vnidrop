package com.vnidrop.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.vnidrop.app.platform.DesktopAppearanceBridge
import com.vnidrop.app.feature.send.DesktopShareBridge
import com.vnidrop.app.feature.receive.ExternalInvitationController
import com.vnidrop.app.feature.receive.MaxVniDropInvitationBytes
import com.vnidrop.app.feature.receive.VniDropInvitationExtension
import java.awt.Desktop
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

fun main(args: Array<String>) {
	val externalInvitations = ExternalInvitationController()
	configureMacOsNativeAppearance()
	configureInvitationOpenHandler(externalInvitations)
	args.asSequence()
		.map(::File)
		.filter { it.extension.equals(VniDropInvitationExtension, ignoreCase = true) }
		.forEach { externalInvitations.openFile(it) }
	DesktopAppearanceBridge.applyNativeAppearance = MacOsAppKitAppearance::apply
	if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
		DesktopShareBridge.shareFile = MacOsShareSheet::share
	}
	application {
		Window(
			onCloseRequest = ::exitApplication,
			title = "vnidrop",
		) {
			App(rememberJvmAppDependencies(externalInvitations))
		}
	}
}

private fun configureInvitationOpenHandler(controller: ExternalInvitationController) {
	if (!Desktop.isDesktopSupported()) return
	val desktop = Desktop.getDesktop()
	if (!desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) return
	desktop.setOpenFileHandler { event -> event.files.forEach(controller::openFile) }
}

private fun ExternalInvitationController.openFile(file: File) {
	val result = runCatching {
		require(file.extension.equals(VniDropInvitationExtension, ignoreCase = true)) { "This is not a VniDrop invitation" }
		val bytes = file.inputStream().use { it.readNBytes(MaxVniDropInvitationBytes + 1) }
		require(bytes.size <= MaxVniDropInvitationBytes) { "The invitation is too large" }
		Charsets.UTF_8.newDecoder()
			.onMalformedInput(CodingErrorAction.REPORT)
			.onUnmappableCharacter(CodingErrorAction.REPORT)
			.decode(ByteBuffer.wrap(bytes))
			.toString()
	}
	result.fold(::openInvitation) { error ->
		reportOpenFailure(error.message ?: "The invitation could not be opened")
	}
}

private fun configureMacOsNativeAppearance() {
	if (!System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) return
	// AWT reads this before creating the first native window. Runtime theme
	// changes are handled in the JVM platform appearance hook.
	System.setProperty("apple.awt.application.appearance", "system")
}
