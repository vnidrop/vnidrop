package com.vnidrop.app.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppVisibility(initiallyForeground: Boolean = true) {
	private val _isForeground = MutableStateFlow(initiallyForeground)
	val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

	fun setForeground(value: Boolean) {
		_isForeground.value = value
	}
}
