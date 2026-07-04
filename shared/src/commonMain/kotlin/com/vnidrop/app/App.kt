package com.vnidrop.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vnidrop.app.core.CoreRepository
import com.vnidrop.app.core.CoreUiState
import com.vnidrop.app.core.PickedShareFile
import com.vnidrop.app.core.rememberShareFilePicker
import com.vnidrop.app.core.sharePickedFile
import com.vnidrop.app.ui.components.AppCard
import com.vnidrop.app.ui.components.ErrorBanner
import com.vnidrop.app.ui.components.Field
import com.vnidrop.app.ui.components.MetadataRow
import com.vnidrop.app.ui.components.PillTone
import com.vnidrop.app.ui.components.PrimaryButton
import com.vnidrop.app.ui.components.ProgressRow
import com.vnidrop.app.ui.components.QuietButton
import com.vnidrop.app.ui.components.SecondaryButton
import com.vnidrop.app.ui.components.StatusPill
import com.vnidrop.app.ui.state.AppDestination
import com.vnidrop.app.ui.state.AppUiState
import com.vnidrop.app.ui.state.ReceiveUiState
import com.vnidrop.app.ui.state.SendUiState
import com.vnidrop.app.ui.state.WindowClass
import com.vnidrop.app.ui.state.activeReceiverRequests
import com.vnidrop.app.ui.state.displayNameForStatus
import com.vnidrop.app.ui.state.formatBytes
import com.vnidrop.app.ui.state.friendlyCoreError
import com.vnidrop.app.ui.state.summarizeProgress
import com.vnidrop.app.ui.state.transferSubtitle
import com.vnidrop.app.ui.state.windowClassFor
import com.vnidrop.app.ui.theme.LocalVniDropColors
import com.vnidrop.app.ui.theme.ThemeMode
import com.vnidrop.app.ui.theme.VniDropTheme
import kotlinx.coroutines.launch
import uniffi.vnidrop.CoreEvent
import uniffi.vnidrop.ReceiverRequest
import uniffi.vnidrop.ShareResult
import uniffi.vnidrop.StoredTransfer
import uniffi.vnidrop.TicketInspection

@Composable
@Preview
fun App() {
	val platform = remember { getPlatform() }
	val repository = remember { CoreRepository() }
	val coreState by repository.state.collectAsState()
	var appState by remember { mutableStateOf(AppUiState()) }
	var appDataDir by remember { mutableStateOf(platform.defaultCoreDataDir) }
	var sendState by remember { mutableStateOf(SendUiState()) }
	var receiveState by remember { mutableStateOf(ReceiveUiState(outputDirectory = platform.defaultReceiveDir)) }
	var selectedFile by remember { mutableStateOf<PickedShareFile?>(null) }
	val scope = rememberCoroutineScope()
	val clipboard = LocalClipboardManager.current
	val picker = rememberShareFilePicker(
		onFilePicked = { file ->
			selectedFile = file
			sendState = sendState.copy(
				selectedSource = file.value,
				selectedDisplayName = file.displayName,
				transferName = if (sendState.transferName == "VniDrop transfer" || sendState.transferName.isBlank()) {
					file.displayName
				} else {
					sendState.transferName
				},
			)
		},
		onError = { error -> scope.launch { repository.setError(error) } },
	)

	LaunchedEffect(coreState.lastShare?.transferId) {
		coreState.lastShare?.let { share -> repository.refreshReceiverRequests(share.transferId) }
	}

	VniDropTheme(mode = appState.themeMode) {
		BoxWithConstraints {
			val windowClass = windowClassFor(maxWidth.value)
			AppFrame(
				appState = appState,
				coreState = coreState,
				windowClass = windowClass,
				onDestinationChange = { appState = appState.copy(destination = it) },
			) {
				when (appState.destination) {
					AppDestination.Send -> SendScreen(
						coreState = coreState,
						sendState = sendState,
						onSendStateChange = { sendState = it },
						onSelectFile = { picker.pickFile() },
						onCreateShare = {
							scope.launch {
								sendState = sendState.copy(isSharing = true)
								val file = selectedFile
								if (file != null) {
									sharePickedFile(repository, file, sendState.transferName, sendState.senderName)
								} else {
									repository.sharePath(sendState.selectedSource, sendState.transferName, sendState.senderName)
								}
								sendState = sendState.copy(isSharing = false)
							}
						},
						onCopyTicket = { ticket -> clipboard.setText(AnnotatedString(ticket)) },
						onUseLocally = { ticket ->
							receiveState = receiveState.copy(ticket = ticket)
							appState = appState.copy(destination = AppDestination.Receive)
						},
						onRefreshRequests = { transferId -> scope.launch { repository.refreshReceiverRequests(transferId) } },
						onRespondRequest = { requestId, accepted ->
							scope.launch { repository.respondReceiverRequest(requestId, accepted, reason = if (accepted) null else "sender-refused") }
						},
					)
					AppDestination.Receive -> ReceiveScreen(
						coreState = coreState,
						receiveState = receiveState,
						onReceiveStateChange = { receiveState = it },
						onInspect = { scope.launch { repository.inspectTicket(receiveState.ticket) } },
						onReceive = {
							scope.launch {
								receiveState = receiveState.copy(isReceiving = true)
								repository.receive(receiveState.ticket, receiveState.outputDirectory, receiveState.receiverName)
								receiveState = receiveState.copy(isReceiving = false)
							}
						},
					)
					AppDestination.Activity -> ActivityScreen(
						coreState = coreState,
						onRefresh = {
							scope.launch {
								repository.refreshTransfers()
								repository.refreshEvents()
							}
						},
						onCancel = { transferId -> scope.launch { repository.cancel(transferId) } },
					)
					AppDestination.Requests -> RequestsScreen(
						requests = coreState.receiverRequests,
						lastShare = coreState.lastShare,
						onRefresh = { transferId -> scope.launch { repository.refreshReceiverRequests(transferId) } },
						onRespond = { requestId, accepted ->
							scope.launch { repository.respondReceiverRequest(requestId, accepted, reason = if (accepted) null else "sender-refused") }
						},
					)
					AppDestination.Settings -> SettingsScreen(
						platformName = platform.name,
						appDataDir = appDataDir,
						onAppDataDirChange = { appDataDir = it },
						coreState = coreState,
						themeMode = appState.themeMode,
						onThemeModeChange = { appState = appState.copy(themeMode = it) },
						diagnosticsVisible = appState.diagnosticsVisible,
						onDiagnosticsVisibleChange = { appState = appState.copy(diagnosticsVisible = it) },
						onInitialize = { scope.launch { repository.initialize(appDataDir) } },
					)
				}

				if (appState.diagnosticsVisible) {
					DiagnosticsPanel(events = coreState.events)
				}
			}
		}
	}
}

@Composable
private fun AppFrame(
	appState: AppUiState,
	coreState: CoreUiState,
	windowClass: WindowClass,
	onDestinationChange: (AppDestination) -> Unit,
	content: @Composable () -> Unit,
) {
	val colors = LocalVniDropColors.current
	Surface(
		modifier = Modifier
			.fillMaxSize()
			.background(colors.canvas)
			.safeContentPadding(),
		color = colors.canvas,
	) {
		if (windowClass == WindowClass.Compact) {
			Column(modifier = Modifier.fillMaxSize()) {
				TopBar(coreState = coreState)
				Box(modifier = Modifier.weight(1f)) {
					ScreenContent(content = content)
				}
				BottomNav(selected = appState.destination, onDestinationChange = onDestinationChange)
			}
		} else {
			Row(modifier = Modifier.fillMaxSize()) {
				SideNav(
					selected = appState.destination,
					coreState = coreState,
					onDestinationChange = onDestinationChange,
				)
				Box(modifier = Modifier.weight(1f)) {
					ScreenContent(content = content)
				}
			}
		}
	}
}

@Composable
private fun ScreenContent(content: @Composable () -> Unit) {
	val colors = LocalVniDropColors.current
	LazyColumn(
		modifier = Modifier
			.fillMaxSize()
			.background(colors.canvas)
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(14.dp),
	) {
		item {
			content()
		}
	}
}

@Composable
private fun TopBar(coreState: CoreUiState) {
	val colors = LocalVniDropColors.current
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.background(colors.sidebar)
			.border(BorderStroke(1.dp, colors.border))
			.padding(horizontal = 16.dp, vertical = 12.dp),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text("VniDrop", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
		NodeStatus(coreState)
	}
}

@Composable
private fun SideNav(
	selected: AppDestination,
	coreState: CoreUiState,
	onDestinationChange: (AppDestination) -> Unit,
) {
	val colors = LocalVniDropColors.current
	Column(
		modifier = Modifier
			.width(220.dp)
			.fillMaxHeight()
			.background(colors.sidebar)
			.border(BorderStroke(1.dp, colors.border))
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(10.dp),
	) {
		Text("VniDrop", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
		Text("Private file transfer", color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
		Spacer(Modifier.height(12.dp))
		AppDestination.entries.forEach { destination ->
			NavItem(
				destination = destination,
				selected = destination == selected,
				onClick = { onDestinationChange(destination) },
			)
		}
		Spacer(Modifier.weight(1f))
		NodeStatus(coreState)
	}
}

@Composable
private fun BottomNav(
	selected: AppDestination,
	onDestinationChange: (AppDestination) -> Unit,
) {
	val colors = LocalVniDropColors.current
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.background(colors.sidebar)
			.border(BorderStroke(1.dp, colors.border))
			.padding(8.dp),
		horizontalArrangement = Arrangement.spacedBy(6.dp),
	) {
		AppDestination.entries.forEach { destination ->
			NavItem(
				destination = destination,
				selected = destination == selected,
				onClick = { onDestinationChange(destination) },
				modifier = Modifier.weight(1f),
				compact = true,
			)
		}
	}
}

@Composable
private fun NavItem(
	destination: AppDestination,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	compact: Boolean = false,
) {
	val colors = LocalVniDropColors.current
	val background = if (selected) colors.surfaceMuted else colors.sidebar
	val border = if (selected) colors.brand.copy(alpha = 0.55f) else colors.border.copy(alpha = 0f)
	Box(
		modifier = modifier
			.clip(RoundedCornerShape(8.dp))
			.background(background)
			.border(1.dp, border, RoundedCornerShape(8.dp))
			.selectable(selected = selected, onClick = onClick)
			.padding(horizontal = if (compact) 6.dp else 12.dp, vertical = 10.dp),
		contentAlignment = Alignment.Center,
	) {
		Text(
			destination.label,
			style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
			color = if (selected) colors.textPrimary else colors.textSecondary,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

@Composable
private fun NodeStatus(coreState: CoreUiState) {
	StatusPill(
		label = if (coreState.isInitialized) "Online" else "Offline",
		tone = if (coreState.isInitialized) PillTone.Success else PillTone.Neutral,
	)
}

@Composable
private fun SendScreen(
	coreState: CoreUiState,
	sendState: SendUiState,
	onSendStateChange: (SendUiState) -> Unit,
	onSelectFile: () -> Unit,
	onCreateShare: () -> Unit,
	onCopyTicket: (String) -> Unit,
	onUseLocally: (String) -> Unit,
	onRefreshRequests: (ULong) -> Unit,
	onRespondRequest: (String, Boolean) -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		ScreenHeader("Send", "Create a VniDrop ticket and approve receivers when required.")
		ErrorSection(coreState)
		AppCard(title = "Source") {
			if (sendState.selectedSource.isBlank()) {
				EmptyText("Select a file to start a share. The app keeps bytes in Rust and platform file handles.")
			} else {
				MetadataRow("Name", sendState.selectedDisplayName.ifBlank { sendState.selectedSource.substringAfterLast('/') })
				MetadataRow("Source", sendState.selectedSource)
			}
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				PrimaryButton("Select file", onClick = onSelectFile)
				SecondaryButton(
					text = "Clear",
					onClick = { onSendStateChange(sendState.copy(selectedSource = "", selectedDisplayName = "")) },
					enabled = sendState.selectedSource.isNotBlank(),
				)
			}
		}
		AppCard(title = "Transfer details") {
			Field(
				value = sendState.transferName,
				onValueChange = { onSendStateChange(sendState.copy(transferName = it)) },
				label = "Transfer name",
			)
			Field(
				value = sendState.senderName,
				onValueChange = { onSendStateChange(sendState.copy(senderName = it)) },
				label = "Sender name",
			)
			PrimaryButton(
				text = if (sendState.isSharing) "Creating ticket..." else "Create share ticket",
				onClick = onCreateShare,
				enabled = coreState.isInitialized && sendState.selectedSource.isNotBlank() && !sendState.isSharing,
			)
		}
		coreState.lastShare?.let { share ->
			ShareResultCard(
				share = share,
				requests = coreState.receiverRequests,
				onCopyTicket = onCopyTicket,
				onUseLocally = onUseLocally,
				onRefreshRequests = onRefreshRequests,
				onRespondRequest = onRespondRequest,
			)
		}
		ProgressSection(coreState)
	}
}

@Composable
private fun ShareResultCard(
	share: ShareResult,
	requests: List<ReceiverRequest>,
	onCopyTicket: (String) -> Unit,
	onUseLocally: (String) -> Unit,
	onRefreshRequests: (ULong) -> Unit,
	onRespondRequest: (String, Boolean) -> Unit,
) {
	AppCard(title = "Share ticket", trailing = {
		StatusPill("${share.fileCount} file${if (share.fileCount == 1UL) "" else "s"}", tone = PillTone.Brand)
	}) {
		MetadataRow("Transfer", share.transferName)
		MetadataRow("Size", formatBytes(share.totalSize))
		SelectionContainer {
			Text(
				text = share.ticket,
				modifier = Modifier
					.fillMaxWidth()
					.clip(RoundedCornerShape(8.dp))
					.background(LocalVniDropColors.current.surfaceMuted)
					.padding(12.dp),
				style = MaterialTheme.typography.bodySmall,
			)
		}
		Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			PrimaryButton("Copy", onClick = { onCopyTicket(share.ticket) })
			SecondaryButton("Use locally", onClick = { onUseLocally(share.ticket) })
			SecondaryButton("Refresh", onClick = { onRefreshRequests(share.transferId) })
		}
		if (requests.isNotEmpty()) {
			HorizontalDivider(color = LocalVniDropColors.current.border)
			ReceiverRequestList(requests = requests, onRespondRequest = onRespondRequest)
		}
	}
}

@Composable
private fun ReceiveScreen(
	coreState: CoreUiState,
	receiveState: ReceiveUiState,
	onReceiveStateChange: (ReceiveUiState) -> Unit,
	onInspect: () -> Unit,
	onReceive: () -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		ScreenHeader("Receive", "Inspect a ticket, request access, and stream files into the output directory.")
		ErrorSection(coreState)
		AppCard(title = "Ticket") {
			Field(
				value = receiveState.ticket,
				onValueChange = { onReceiveStateChange(receiveState.copy(ticket = it)) },
				label = "Ticket",
				minLines = 4,
			)
			Field(
				value = receiveState.outputDirectory,
				onValueChange = { onReceiveStateChange(receiveState.copy(outputDirectory = it)) },
				label = "Output directory",
			)
			Field(
				value = receiveState.receiverName,
				onValueChange = { onReceiveStateChange(receiveState.copy(receiverName = it)) },
				label = "Receiver name",
			)
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				SecondaryButton(
					text = "Inspect ticket",
					onClick = onInspect,
					enabled = coreState.isInitialized && receiveState.ticket.isNotBlank(),
				)
				PrimaryButton(
					text = if (receiveState.isReceiving) "Receiving..." else "Receive",
					onClick = onReceive,
					enabled = coreState.isInitialized &&
						receiveState.ticket.isNotBlank() &&
						receiveState.outputDirectory.isNotBlank() &&
						!receiveState.isReceiving,
				)
			}
		}
		coreState.lastInspection?.let { TicketInspectionCard(it) }
		ProgressSection(coreState)
	}
}

@Composable
private fun TicketInspectionCard(inspection: TicketInspection) {
	AppCard(title = "Ticket details") {
		MetadataRow("Kind", inspection.kind)
		inspection.metadata?.let { metadata ->
			MetadataRow("Transfer", metadata.transferName)
			MetadataRow("Sender", metadata.senderName ?: "Unknown")
			MetadataRow("Files", metadata.fileCount.toString())
			MetadataRow("Size", formatBytes(metadata.totalSize))
			MetadataRow("Hash", metadata.contentHash)
		} ?: EmptyText("This ticket does not include VniDrop metadata.")
	}
}

@Composable
private fun ActivityScreen(
	coreState: CoreUiState,
	onRefresh: () -> Unit,
	onCancel: (ULong) -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		ScreenHeader("Activity", "Follow current and recent transfers from the Rust core.")
		ErrorSection(coreState)
		AppCard(title = "Transfers", trailing = { SecondaryButton("Refresh", onClick = onRefresh) }) {
			if (coreState.transfers.isEmpty()) {
				EmptyText("No transfers yet.")
			} else {
				coreState.transfers.forEach { transfer ->
					TransferRow(transfer = transfer, onCancel = onCancel)
				}
			}
		}
		ProgressSection(coreState)
	}
}

@Composable
private fun TransferRow(transfer: StoredTransfer, onCancel: (ULong) -> Unit) {
	val status = displayNameForStatus(transfer.status)
	val tone = when (transfer.status.lowercase()) {
		"done" -> PillTone.Success
		"failed" -> PillTone.Destructive
		"cancelled", "stopped" -> PillTone.Warning
		"sharing", "receiving" -> PillTone.Brand
		else -> PillTone.Neutral
	}
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.background(LocalVniDropColors.current.surfaceRaised)
			.padding(12.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
			Column(modifier = Modifier.weight(1f)) {
				Text(transfer.transferName ?: "Transfer ${transfer.transferId}", fontWeight = FontWeight.SemiBold)
				Text(transferSubtitle(transfer), color = LocalVniDropColors.current.textMuted, style = MaterialTheme.typography.bodySmall)
			}
			StatusPill(status, tone = tone)
		}
		if (transfer.status == "sharing" || transfer.status == "receiving") {
			QuietButton("Cancel", onClick = { onCancel(transfer.transferId) })
		}
	}
}

@Composable
private fun RequestsScreen(
	requests: List<ReceiverRequest>,
	lastShare: ShareResult?,
	onRefresh: (ULong) -> Unit,
	onRespond: (String, Boolean) -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		ScreenHeader("Requests", "Approve or refuse receivers for the current share.")
		AppCard(title = "Receiver requests", trailing = {
			lastShare?.let { SecondaryButton("Refresh", onClick = { onRefresh(it.transferId) }) }
		}) {
			if (lastShare == null) {
				EmptyText("Create a share ticket first.")
			} else if (requests.isEmpty()) {
				EmptyText("No receiver requests yet.")
			} else {
				ReceiverRequestList(requests = requests, onRespondRequest = onRespond)
			}
		}
	}
}

@Composable
private fun ReceiverRequestList(
	requests: List<ReceiverRequest>,
	onRespondRequest: (String, Boolean) -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
		requests.forEach { request ->
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.clip(RoundedCornerShape(8.dp))
					.background(LocalVniDropColors.current.surfaceRaised)
					.padding(12.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
					Column(modifier = Modifier.weight(1f)) {
						Text(request.receiverName ?: "Receiver", fontWeight = FontWeight.SemiBold)
						Text(request.remoteEndpointId.take(28), color = LocalVniDropColors.current.textMuted, style = MaterialTheme.typography.bodySmall)
					}
					StatusPill(displayNameForStatus(request.status), tone = if (request.status == "requested") PillTone.Warning else PillTone.Neutral)
				}
				request.reason?.let { Text(it, color = LocalVniDropColors.current.textMuted, style = MaterialTheme.typography.bodySmall) }
				if (request.status == "requested") {
					Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
						SecondaryButton("Refuse", onClick = { onRespondRequest(request.id, false) })
						PrimaryButton("Approve", onClick = { onRespondRequest(request.id, true) })
					}
				}
			}
		}
	}
}

@Composable
private fun SettingsScreen(
	platformName: String,
	appDataDir: String,
	onAppDataDirChange: (String) -> Unit,
	coreState: CoreUiState,
	themeMode: ThemeMode,
	onThemeModeChange: (ThemeMode) -> Unit,
	diagnosticsVisible: Boolean,
	onDiagnosticsVisibleChange: (Boolean) -> Unit,
	onInitialize: () -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		ScreenHeader("Settings", "Configure the local node and app appearance.")
		ErrorSection(coreState)
		AppCard(title = "Node") {
			MetadataRow("Platform", platformName)
			MetadataRow("Status", coreState.status)
			Field(value = appDataDir, onValueChange = onAppDataDirChange, label = "Core data directory")
			PrimaryButton("Initialize core", onClick = onInitialize)
		}
		AppCard(title = "Appearance") {
			ThemeMode.entries.forEach { mode ->
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(8.dp))
						.selectable(selected = themeMode == mode, onClick = { onThemeModeChange(mode) })
						.padding(vertical = 8.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					RadioButton(selected = themeMode == mode, onClick = { onThemeModeChange(mode) })
					Text(mode.name)
				}
			}
		}
		AppCard(title = "Diagnostics") {
			SecondaryButton(
				text = if (diagnosticsVisible) "Hide event log" else "Show event log",
				onClick = { onDiagnosticsVisibleChange(!diagnosticsVisible) },
			)
			EmptyText("Diagnostics are intentionally separate from the primary flow so transfer state stays readable.")
		}
	}
}

@Composable
private fun DiagnosticsPanel(events: List<CoreEvent>) {
	AppCard(title = "Event log") {
		if (events.isEmpty()) {
			EmptyText("No events have been emitted yet.")
		} else {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.height(280.dp)
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				events.forEach { event ->
					EventRow(event)
				}
			}
		}
	}
}

@Composable
private fun EventRow(event: CoreEvent) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.background(LocalVniDropColors.current.surfaceRaised)
			.padding(10.dp),
		verticalArrangement = Arrangement.spacedBy(4.dp),
	) {
		Text("${event.scope}/${event.direction ?: "-"} ${event.phase}:${event.kind}", style = MaterialTheme.typography.bodySmall)
		Text(event.dataJson, color = LocalVniDropColors.current.textMuted, style = MaterialTheme.typography.bodySmall)
	}
}

@Composable
private fun ProgressSection(coreState: CoreUiState) {
	val progress = summarizeProgress(coreState.events)
	if (progress.isNotEmpty()) {
		AppCard(title = "Progress") {
			progress.forEach { item ->
				ProgressRow(label = item.label, progress = item.progress)
			}
		}
	}
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
		Text(subtitle, color = LocalVniDropColors.current.textMuted, style = MaterialTheme.typography.bodyMedium)
	}
}

@Composable
private fun ErrorSection(coreState: CoreUiState) {
	friendlyCoreError(coreState.error)?.let { ErrorBanner(it) }
}

@Composable
private fun EmptyText(text: String) {
	Text(text, color = LocalVniDropColors.current.textMuted, style = MaterialTheme.typography.bodyMedium)
}
