package com.vnidrop.app.support

import com.vnidrop.app.core.CoreGateway
import com.vnidrop.app.core.CoreSignal
import com.vnidrop.app.core.CoreState
import com.vnidrop.app.core.FileSystemService
import com.vnidrop.app.core.FolderAccessStatus
import com.vnidrop.app.core.PickedShareFile
import com.vnidrop.app.core.ReceiveFolder
import com.vnidrop.app.core.ReceiverRequestModel
import com.vnidrop.app.core.Share
import com.vnidrop.app.core.TicketInspectionModel
import com.vnidrop.app.notifications.LocalNotification
import com.vnidrop.app.notifications.LocalNotificationService
import com.vnidrop.app.notifications.NotificationPermission
import com.vnidrop.app.preferences.AppPreferences
import com.vnidrop.app.preferences.PreferencesRepository
import com.vnidrop.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import uniffi.vnidrop.ReceiveOutputSink

class FakeCoreGateway : CoreGateway {
	val mutableState = MutableStateFlow(CoreState())
	override val state: StateFlow<CoreState> = mutableState
	val mutableSignals = MutableSharedFlow<CoreSignal>(extraBufferCapacity = 16)
	override val signals: SharedFlow<CoreSignal> = mutableSignals
	val requests = mutableMapOf<ULong, List<ReceiverRequestModel>>()
	var responseResult: Result<Unit> = Result.success(Unit)
	val responses = mutableListOf<Triple<String, Boolean, String?>>()

	override suspend fun initialize(appDataDir: String): Result<Unit> {
		mutableState.value = mutableState.value.copy(isInitialized = true)
		return Result.success(Unit)
	}
	override fun shutdown() = Unit
	override suspend fun sharePath(path: String, transferName: String, senderName: String) = Result.failure<Share>(UnsupportedOperationException())
	override suspend fun shareFileDescriptor(fd: Int, displayName: String, transferName: String, senderName: String) = Result.failure<Share>(UnsupportedOperationException())
	override suspend fun shareSecurityScopedFileUrl(fileUrl: String, displayName: String, transferName: String, senderName: String) = Result.failure<Share>(UnsupportedOperationException())
	override suspend fun inspectTicket(ticket: String) = Result.failure<TicketInspectionModel>(UnsupportedOperationException())
	override suspend fun receive(ticket: String, outputDir: String, receiverName: String) = Result.success(Unit)
	override suspend fun receiveWithOutputSink(ticket: String, outputSink: ReceiveOutputSink, receiverName: String) = Result.success(Unit)
	override suspend fun receiveIntoSecurityScopedDirectory(ticket: String, outputDirectoryUrl: String, receiverName: String) = Result.success(Unit)
	override suspend fun cancel(transferId: ULong) = Result.success(Unit)
	override suspend fun receiverRequests(transferId: ULong) = Result.success(requests[transferId].orEmpty())
	override suspend fun respondReceiverRequest(requestId: String, accepted: Boolean, reason: String?): Result<Unit> {
		responses += Triple(requestId, accepted, reason)
		return responseResult
	}
	override suspend fun refresh() = Result.success(Unit)
}

class FakePreferencesRepository(
	initial: AppPreferences,
) : PreferencesRepository {
	val mutablePreferences = MutableStateFlow(initial)
	override val preferences = mutablePreferences
	override suspend fun setUsername(username: String) { mutablePreferences.value = mutablePreferences.value.copy(username = username) }
	override suspend fun setReceiveFolder(folder: ReceiveFolder) { mutablePreferences.value = mutablePreferences.value.copy(receiveFolder = folder) }
	override suspend fun resetReceiveFolder() = Unit
	override suspend fun setThemeMode(mode: ThemeMode) { mutablePreferences.value = mutablePreferences.value.copy(themeMode = mode) }
	override suspend fun setNotificationsEnabled(enabled: Boolean) { mutablePreferences.value = mutablePreferences.value.copy(notificationsEnabled = enabled) }
}

class FakeNotificationService(
	permission: NotificationPermission = NotificationPermission.Granted,
) : LocalNotificationService {
	val mutablePermission = MutableStateFlow(permission)
	override val permission: StateFlow<NotificationPermission> = mutablePermission
	val published = mutableListOf<LocalNotification>()
	val cancelled = mutableListOf<String>()
	var cancelAllCount = 0
	var openSettingsCount = 0
	var openSettingsResult: Result<Unit> = Result.success(Unit)
	override suspend fun refreshPermission() = permission.value
	override suspend fun requestPermission() = permission.value
	override suspend fun openSettings(): Result<Unit> = openSettingsResult.also { openSettingsCount += 1 }
	override suspend fun publish(notification: LocalNotification): Result<Unit> = Result.success(Unit).also { published += notification }
	override suspend fun cancel(id: String) { cancelled += id }
	override suspend fun cancelAll() { cancelAllCount += 1 }
}

class FakeFileSystemService(
	private val folder: ReceiveFolder,
) : FileSystemService {
	override fun defaultReceiveFolder() = folder
	override suspend fun validateReceiveFolder(folder: ReceiveFolder) = FolderAccessStatus.Writable
	override fun createReceiveOutputSink(folder: ReceiveFolder): ReceiveOutputSink? = null
	override suspend fun sharePickedFile(
		repository: CoreGateway,
		file: PickedShareFile,
		transferName: String,
		senderName: String,
	) = repository.sharePath(file.value, transferName, senderName)
}
