package com.aura.music.ui.license

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.core.license.LicenseRepository
import com.aura.music.core.license.LicenseStatus
import com.aura.music.core.license.RedeemResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LicenseViewModel @Inject constructor(
    private val repository: LicenseRepository
) : ViewModel() {

    val licenseStatus: StateFlow<LicenseStatus> = repository.licenseStatus
    val deviceId: String = repository.getDeviceId()

    var serialInput by mutableStateOf("")
    var isProcessing by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var successMessage by mutableStateOf<String?>(null)
        private set

    fun onSerialChange(newVal: String) {
        serialInput = newVal
        errorMessage = null
    }

    fun redeem() {
        val trimmed = serialInput.trim()
        if (trimmed.isBlank()) {
            errorMessage = "Por favor ingresa o pega un serial."
            return
        }

        isProcessing = true
        errorMessage = null
        successMessage = null

        viewModelScope.launch {
            when (val result = repository.redeemSerial(trimmed)) {
                is RedeemResult.Success -> {
                    serialInput = ""
                    successMessage = if (result.isLifetime) {
                        "¡Licencia Vitalicia activada con éxito!"
                    } else {
                        "¡Licencia activada! Se sumaron ${result.daysAdded} días (Total: ${result.totalDaysRemaining} días)."
                    }
                }
                is RedeemResult.Error -> {
                    errorMessage = result.message
                }
            }
            isProcessing = false
        }
    }

    fun copyDeviceId(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Device ID", deviceId)
        clipboard.setPrimaryClip(clip)
    }

    fun pasteFromClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString().orEmpty()
            if (text.isNotBlank()) {
                serialInput = text.trim()
                errorMessage = null
            }
        }
    }

    fun clearMessages() {
        errorMessage = null
        successMessage = null
    }

    fun refresh() {
        repository.refreshStatus()
    }
}
