package com.renotube.tv.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liskovsoft.mediaserviceinterfaces.SignInService
import com.renotube.tv.ambilight.AmbilightConfig
import com.renotube.tv.ambilight.AmbilightRepository
import com.renotube.tv.ambilight.WledInfoResult
import com.renotube.tv.data.repository.UserHistoryRepository
import com.renotube.tv.player.RenoTubePlayer
import com.renotube.tv.remote.DEFAULT_WEB_REMOTE_PORT
import com.renotube.tv.remote.WebRemoteServer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class TestConnectionState {
    object Idle : TestConnectionState()
    object Testing : TestConnectionState()
    object Success : TestConnectionState()
    data class Error(val message: String) : TestConnectionState()
}

sealed class WledDetectState {
    object Idle : WledDetectState()
    object Detecting : WledDetectState()
    data class Discovered(val info: WledInfoResult) : WledDetectState()
    data class Failed(val message: String) : WledDetectState()
}

data class AccountUiState(
    val isSigned: Boolean = false,
    val accountName: String = "Guest Session",
    val accountEmail: String = "Not signed in",
    val avatarUrl: String = "",
    val signInCode: String? = null,
    val isSigningIn: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ambilightRepository: AmbilightRepository,
    private val renoTubePlayer: RenoTubePlayer,
    private val userHistoryRepository: UserHistoryRepository,
    private val signInService: SignInService
) : ViewModel() {

    val tvIpAddress: String = WebRemoteServer.getLocalIpAddress(context)
    val webRemoteUrl: String = "http://$tvIpAddress:$DEFAULT_WEB_REMOTE_PORT"

    private val _accountState = MutableStateFlow(AccountUiState())
    val accountState: StateFlow<AccountUiState> = _accountState.asStateFlow()

    fun refreshAccount() {
        val signed = signInService.isSigned
        val acc = signInService.selectedAccount
        _accountState.update {
            it.copy(
                isSigned = signed,
                accountName = acc?.name ?: "Guest Session",
                accountEmail = acc?.email ?: "Not signed in",
                avatarUrl = acc?.avatarImageUrl ?: "",
                isSigningIn = false
            )
        }
    }

    fun startSignIn() {
        _accountState.update { it.copy(isSigningIn = true, signInCode = null) }
        viewModelScope.launch(Dispatchers.IO) {
            signInService.signInObserve()
                .subscribe(
                    { code ->
                        _accountState.update { it.copy(signInCode = code, isSigningIn = false) }
                    },
                    { err ->
                        _accountState.update { it.copy(isSigningIn = false) }
                    },
                    {
                        refreshAccount()
                    }
                )
        }
    }

    fun signOut() {
        val acc = signInService.selectedAccount
        if (acc != null) {
            signInService.removeAccount(acc)
        }
        refreshAccount()
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            userHistoryRepository.clearHistory()
        }
    }

    private val playerPrefs = context.getSharedPreferences("renotube_player_prefs", Context.MODE_PRIVATE)
    private val _autoplayEnabled = MutableStateFlow(playerPrefs.getBoolean("autoplay_next", true))
    val autoplayEnabled: StateFlow<Boolean> = _autoplayEnabled.asStateFlow()

    private val _voiceBoostEnabled = MutableStateFlow(renoTubePlayer.isVoiceBoostEnabled())
    val voiceBoostEnabled: StateFlow<Boolean> = _voiceBoostEnabled.asStateFlow()

    private val _afrEnabled = MutableStateFlow(renoTubePlayer.isAfrEnabled())
    val afrEnabled: StateFlow<Boolean> = _afrEnabled.asStateFlow()

    private val _blockedChannelCount = MutableStateFlow(0)
    val blockedChannelCount: StateFlow<Int> = _blockedChannelCount.asStateFlow()

    private val _sponsorBlockEnabled = MutableStateFlow(playerPrefs.getBoolean("sponsorblock_enabled", true))
    val sponsorBlockEnabled: StateFlow<Boolean> = _sponsorBlockEnabled.asStateFlow()

    fun toggleSponsorBlock(enabled: Boolean) {
        playerPrefs.edit().putBoolean("sponsorblock_enabled", enabled).apply()
        _sponsorBlockEnabled.value = enabled
    }

    private val _adBlockEnabled = MutableStateFlow(playerPrefs.getBoolean("adblock_enabled", true))
    val adBlockEnabled: StateFlow<Boolean> = _adBlockEnabled.asStateFlow()

    fun toggleAdBlock(enabled: Boolean) {
        playerPrefs.edit().putBoolean("adblock_enabled", enabled).apply()
        _adBlockEnabled.value = enabled
    }

    init {
        loadBlockedCount()
        refreshAccount()
    }

    fun loadBlockedCount() {
        viewModelScope.launch {
            _blockedChannelCount.value = userHistoryRepository.getBlockedChannels().size
        }
    }

    fun toggleVoiceBoost(enabled: Boolean) {
        renoTubePlayer.setVoiceBoostEnabled(enabled)
        _voiceBoostEnabled.value = enabled
    }

    fun toggleAfr(enabled: Boolean) {
        renoTubePlayer.setAfrEnabled(enabled)
        _afrEnabled.value = enabled
    }

    fun clearBlockedChannels() {
        viewModelScope.launch {
            userHistoryRepository.clearBlockedContent()
            _blockedChannelCount.value = 0
        }
    }

    fun toggleAutoplay(enabled: Boolean) {
        playerPrefs.edit().putBoolean("autoplay_next", enabled).apply()
        _autoplayEnabled.value = enabled
    }

    val config: StateFlow<AmbilightConfig> = ambilightRepository.config

    private val _testStatus = MutableStateFlow<TestConnectionState>(TestConnectionState.Idle)
    val testStatus: StateFlow<TestConnectionState> = _testStatus.asStateFlow()

    private val _detectStatus = MutableStateFlow<WledDetectState>(WledDetectState.Idle)
    val detectStatus: StateFlow<WledDetectState> = _detectStatus.asStateFlow()

    fun updateConfig(newConfig: AmbilightConfig) {
        ambilightRepository.updateConfig(newConfig)
    }

    fun toggleAmbilight(enabled: Boolean) {
        val current = config.value
        ambilightRepository.updateConfig(current.copy(enabled = enabled))
        if (enabled) {
            setWarmWhite()
        }
    }

    fun setWarmWhite() {
        val cfg = config.value
        if (cfg.enabled) {
            viewModelScope.launch(Dispatchers.IO) {
                ambilightRepository.setWarmWhite()
            }
        }
    }

    fun setWledIp(ip: String) {
        val current = config.value
        ambilightRepository.updateConfig(current.copy(wledIp = ip.trim()))
    }

    fun setBrightness(brightness: Float) {
        val current = config.value
        ambilightRepository.updateConfig(current.copy(brightness = brightness))
    }

    fun setSaturationGain(gain: Float) {
        val current = config.value
        ambilightRepository.updateConfig(current.copy(saturationGain = gain))
    }

    fun setColorTemperature(temp: String) {
        val current = config.value
        ambilightRepository.updateConfig(current.copy(colorTemperature = temp))
    }

    fun setLayoutGeometry(startCorner: String, direction: String, bottomGap: Int) {
        val current = config.value
        ambilightRepository.updateConfig(
            current.copy(
                startCorner = startCorner,
                direction = direction,
                bottomGapLeds = bottomGap
            )
        )
    }

    fun setSmoothFactor(factor: Float) {
        val current = config.value
        ambilightRepository.updateConfig(current.copy(smoothFactor = factor))
    }

    fun toggleAmbientGlow(keep: Boolean) {
        val current = config.value
        ambilightRepository.updateConfig(current.copy(keepAmbientGlow = keep))
    }

    fun toggleLetterboxProtection(protect: Boolean) {
        val current = config.value
        ambilightRepository.updateConfig(current.copy(letterboxProtection = protect))
    }

    fun setLedCounts(top: Int, right: Int, bottom: Int, left: Int) {
        val current = config.value
        ambilightRepository.updateConfig(
            current.copy(
                topLeds = top.coerceAtLeast(1),
                rightLeds = right.coerceAtLeast(1),
                bottomLeds = bottom.coerceAtLeast(1),
                leftLeds = left.coerceAtLeast(1)
            )
        )
    }

    fun detectWledDevice() {
        val currentIp = config.value.wledIp
        viewModelScope.launch {
            _detectStatus.value = WledDetectState.Detecting
            val result = ambilightRepository.detectWledDevice(currentIp)
            result.fold(
                onSuccess = { info ->
                    _detectStatus.value = WledDetectState.Discovered(info)
                },
                onFailure = { err ->
                    _detectStatus.value = WledDetectState.Failed(err.message ?: "Could not connect to WLED app")
                }
            )
            delay(5000)
            _detectStatus.value = WledDetectState.Idle
        }
    }

    fun testWledConnection() {
        viewModelScope.launch {
            _testStatus.value = TestConnectionState.Testing
            val success = ambilightRepository.testWledConnection()
            if (success) {
                _testStatus.value = TestConnectionState.Success
            } else {
                _testStatus.value = TestConnectionState.Error("Failed to reach WLED ESP32. Check IP.")
            }
        }
    }

    private val _isEdgeTestActive = MutableStateFlow(false)
    val isEdgeTestActive: StateFlow<Boolean> = _isEdgeTestActive.asStateFlow()

    private val _isCornerTestActive = MutableStateFlow(false)
    val isCornerTestActive: StateFlow<Boolean> = _isCornerTestActive.asStateFlow()

    fun testEdgeCalibration() {
        viewModelScope.launch {
            _isEdgeTestActive.value = true
            _isCornerTestActive.value = false
            _testStatus.value = TestConnectionState.Testing
            val success = ambilightRepository.sendEdgeCalibrationPattern()
            if (success) {
                _testStatus.value = TestConnectionState.Success
            } else {
                _testStatus.value = TestConnectionState.Error("Failed to reach WLED ESP32. Check IP.")
            }
        }
    }

    fun stopEdgeCalibration() {
        _isEdgeTestActive.value = false
        _testStatus.value = TestConnectionState.Idle
        viewModelScope.launch {
            ambilightRepository.setWarmWhite()
        }
    }

    fun testCornerCalibration() {
        viewModelScope.launch {
            _isCornerTestActive.value = true
            _isEdgeTestActive.value = false
            _testStatus.value = TestConnectionState.Testing
            val success = ambilightRepository.sendCornerCalibrationPattern()
            if (success) {
                _testStatus.value = TestConnectionState.Success
            } else {
                _testStatus.value = TestConnectionState.Error("Failed to reach WLED ESP32. Check IP.")
            }
        }
    }

    fun stopCornerCalibration() {
        _isCornerTestActive.value = false
        _testStatus.value = TestConnectionState.Idle
        viewModelScope.launch {
            ambilightRepository.setWarmWhite()
        }
    }

    fun adjustLedCount(edge: String, delta: Int) {
        val current = config.value
        val updated = when (edge) {
            "top" -> current.copy(topLeds = (current.topLeds + delta).coerceAtLeast(1))
            "right" -> current.copy(rightLeds = (current.rightLeds + delta).coerceAtLeast(1))
            "bottom" -> current.copy(bottomLeds = (current.bottomLeds + delta).coerceAtLeast(1))
            "left" -> current.copy(leftLeds = (current.leftLeds + delta).coerceAtLeast(1))
            else -> current
        }
        ambilightRepository.updateConfig(updated)
        viewModelScope.launch {
            if (_isCornerTestActive.value) {
                ambilightRepository.sendCornerCalibrationPattern()
            } else if (_isEdgeTestActive.value) {
                ambilightRepository.sendEdgeCalibrationPattern()
            }
        }
    }
}
