package com.vnidrop.app

import androidx.compose.ui.window.ComposeUIViewController
import com.vnidrop.app.feature.receive.ExternalInvitationController

fun MainViewController(externalInvitations: ExternalInvitationController) =
	ComposeUIViewController { App(rememberIosAppDependencies(externalInvitations)) }
