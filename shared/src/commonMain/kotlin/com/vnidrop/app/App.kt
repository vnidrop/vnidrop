package com.vnidrop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vnidrop.app.core.CoreRepository
import com.vnidrop.app.core.PickedShareFile
import com.vnidrop.app.core.rememberShareFilePicker
import com.vnidrop.app.core.sharePickedFile
import kotlinx.coroutines.launch

@Composable
@Preview
fun App() {
	val platform = remember { getPlatform() }
	val repository = remember { CoreRepository() }
	val state by repository.state.collectAsState()
	val scope = rememberCoroutineScope()
	var appDataDir by remember { mutableStateOf(platform.defaultCoreDataDir) }
	var sourcePath by remember { mutableStateOf("") }
	var selectedFile by remember { mutableStateOf<PickedShareFile?>(null) }
	var transferName by remember { mutableStateOf("VniDrop transfer") }
	var senderName by remember { mutableStateOf("") }
	var ticket by remember { mutableStateOf("") }
	var receiveDir by remember { mutableStateOf(platform.defaultReceiveDir) }
	var receiverName by remember { mutableStateOf("") }
	val shareFilePicker = rememberShareFilePicker(
		onFilePicked = { file ->
			selectedFile = file
			sourcePath = file.value
			if (transferName.isBlank() || transferName == "VniDrop transfer") {
				transferName = file.displayName
			}
		},
		onError = { error -> scope.launch { repository.setError(error) } },
	)

	MaterialTheme {
		Surface(
			modifier = Modifier
				.safeContentPadding()
				.fillMaxSize()
		) {
			LazyColumn(
				modifier = Modifier
					.fillMaxSize()
					.padding(16.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				item {
					Text("VniDrop Core", style = MaterialTheme.typography.headlineMedium)
					Text(platform.name, style = MaterialTheme.typography.bodySmall)
					Text(state.status, style = MaterialTheme.typography.bodyMedium)
				}

				item {
					CoreCard(title = "Initialize") {
						OutlinedTextField(
							value = appDataDir,
							onValueChange = { appDataDir = it },
							label = { Text("Core data directory") },
							modifier = Modifier.fillMaxWidth(),
						)
						Button(onClick = {
							scope.launch { repository.initialize(appDataDir) }
						}) {
							Text("Initialize core")
						}
					}
				}

				item {
					CoreCard(title = "Share file") {
						OutlinedTextField(
							value = sourcePath,
							onValueChange = {
								sourcePath = it
								selectedFile = null
							},
							label = { Text("Selected file") },
							modifier = Modifier.fillMaxWidth(),
						)
						Button(onClick = { shareFilePicker.pickFile() }) {
							Text("Select file")
						}
						OutlinedTextField(
							value = transferName,
							onValueChange = { transferName = it },
							label = { Text("Transfer name") },
							modifier = Modifier.fillMaxWidth(),
						)
						OutlinedTextField(
							value = senderName,
							onValueChange = { senderName = it },
							label = { Text("Sender name") },
							modifier = Modifier.fillMaxWidth(),
						)
						Button(
							enabled = state.isInitialized && (selectedFile != null || sourcePath.isNotBlank()),
							onClick = {
								scope.launch {
									val picked = selectedFile
									if (picked != null) {
										sharePickedFile(repository, picked, transferName, senderName)
									} else {
										repository.sharePath(sourcePath, transferName, senderName)
									}
								}
							},
						) {
							Text("Create share ticket")
						}
						state.lastShare?.let { share ->
							Text("Ticket")
							Text(share.ticket, style = MaterialTheme.typography.bodySmall)
						}
					}
				}

				item {
					CoreCard(title = "Receive") {
						OutlinedTextField(
							value = ticket,
							onValueChange = { ticket = it },
							label = { Text("Ticket") },
							modifier = Modifier.fillMaxWidth(),
							minLines = 3,
						)
						OutlinedTextField(
							value = receiveDir,
							onValueChange = { receiveDir = it },
							label = { Text("Output directory") },
							modifier = Modifier.fillMaxWidth(),
						)
						OutlinedTextField(
							value = receiverName,
							onValueChange = { receiverName = it },
							label = { Text("Receiver name") },
							modifier = Modifier.fillMaxWidth(),
						)
						Button(
							enabled = state.isInitialized && ticket.isNotBlank(),
							onClick = { scope.launch { repository.inspectTicket(ticket) } },
						) {
							Text("Inspect ticket")
						}
						Button(
							enabled = state.isInitialized && ticket.isNotBlank() && receiveDir.isNotBlank(),
							onClick = {
								scope.launch { repository.receive(ticket, receiveDir, receiverName) }
							},
						) {
							Text("Receive")
						}
						state.lastInspection?.let { inspection ->
							Text("Kind: ${inspection.kind}")
							Text("Blob ticket: ${inspection.blobTicket.take(96)}")
							inspection.metadata?.let { metadata ->
								Text("${metadata.transferName} | ${metadata.fileCount} files | ${metadata.totalSize} bytes")
							}
						}
					}
				}

				state.error?.let { error ->
					item {
						Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
							Text(
								text = error,
								modifier = Modifier.padding(12.dp),
								color = MaterialTheme.colorScheme.onErrorContainer,
							)
						}
					}
				}

				item {
					Text("Events", style = MaterialTheme.typography.titleMedium)
				}
				items(state.events, key = { it.id }) { event ->
					Card {
						Text(
							text = "${event.scope}/${event.direction ?: "-"} ${event.phase}:${event.kind}",
							modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp),
							style = MaterialTheme.typography.bodyMedium,
						)
						Text(
							text = event.dataJson,
							modifier = Modifier.padding(start = 12.dp, bottom = 10.dp, end = 12.dp),
							style = MaterialTheme.typography.bodySmall,
						)
					}
				}
			}
		}
	}
}

@Composable
private fun CoreCard(
	title: String,
	content: @Composable () -> Unit,
) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(
			modifier = Modifier.padding(12.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Text(title, style = MaterialTheme.typography.titleMedium)
			HorizontalDivider()
			content()
		}
	}
}
