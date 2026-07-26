package com.robolig.controller.utils

import android.content.SharedPreferences
import com.robolig.controller.core.LogLevel
import com.robolig.controller.core.PreferenceConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface ControllerPreferences {
    val videoStreamUrl: StateFlow<String>
    val logLevel: StateFlow<LogLevel>
    val showPacketsOverlay: StateFlow<Boolean>
    val useDeviceCamera: StateFlow<Boolean>
    val cubeDetectionEnabled: StateFlow<Boolean>

    fun updateVideoStreamUrl(url: String)

    fun updateLogLevel(level: LogLevel)

    fun updateShowPacketsOverlay(enabled: Boolean)

    fun updateUseDeviceCamera(enabled: Boolean)

    fun updateCubeDetectionEnabled(enabled: Boolean)

    fun getRfidUid(city: com.robolig.controller.domain.model.City): String?

    fun setRfidUid(
        city: com.robolig.controller.domain.model.City,
        uid: String?,
    )
}

@Singleton
class ControllerPreferencesImpl
    @Inject
    constructor(
        private val sharedPreferences: SharedPreferences,
    ) : ControllerPreferences {
        private val videoStreamUrlState =
            MutableStateFlow(
                sharedPreferences.getString(PreferenceConstants.VIDEO_STREAM_URL, "").orEmpty(),
            )
        private val logLevelState =
            MutableStateFlow(
                LogLevel.fromPersistedValue(
                    sharedPreferences.getString(PreferenceConstants.LOG_LEVEL, LogLevel.DEBUG.name),
                ),
            )
        private val showPacketsOverlayState =
            MutableStateFlow(
                sharedPreferences.getBoolean(PreferenceConstants.SHOW_PACKETS_OVERLAY, false),
            )
        private val useDeviceCameraState =
            MutableStateFlow(
                sharedPreferences.getBoolean(PreferenceConstants.USE_DEVICE_CAMERA, false),
            )
        private val cubeDetectionEnabledState =
            MutableStateFlow(
                sharedPreferences.getBoolean(PreferenceConstants.CUBE_DETECTION_ENABLED, false),
            )

        override val videoStreamUrl: StateFlow<String> = videoStreamUrlState.asStateFlow()
        override val logLevel: StateFlow<LogLevel> = logLevelState.asStateFlow()
        override val showPacketsOverlay: StateFlow<Boolean> = showPacketsOverlayState.asStateFlow()
        override val useDeviceCamera: StateFlow<Boolean> = useDeviceCameraState.asStateFlow()
        override val cubeDetectionEnabled: StateFlow<Boolean> = cubeDetectionEnabledState.asStateFlow()

        override fun updateVideoStreamUrl(url: String) {
            val normalizedUrl = url.trim()
            if (videoStreamUrlState.value == normalizedUrl) {
                return
            }

            sharedPreferences
                .edit()
                .putString(PreferenceConstants.VIDEO_STREAM_URL, normalizedUrl)
                .apply()

            videoStreamUrlState.value = normalizedUrl
        }

        override fun updateLogLevel(level: LogLevel) {
            if (logLevelState.value == level) {
                return
            }

            sharedPreferences
                .edit()
                .putString(PreferenceConstants.LOG_LEVEL, level.name)
                .apply()

            logLevelState.value = level
        }

        override fun updateShowPacketsOverlay(enabled: Boolean) {
            if (showPacketsOverlayState.value == enabled) {
                return
            }

            sharedPreferences
                .edit()
                .putBoolean(PreferenceConstants.SHOW_PACKETS_OVERLAY, enabled)
                .apply()

            showPacketsOverlayState.value = enabled
        }

        override fun updateUseDeviceCamera(enabled: Boolean) {
            if (useDeviceCameraState.value == enabled) {
                return
            }

            sharedPreferences
                .edit()
                .putBoolean(PreferenceConstants.USE_DEVICE_CAMERA, enabled)
                .apply()

            useDeviceCameraState.value = enabled
        }

        override fun updateCubeDetectionEnabled(enabled: Boolean) {
            if (cubeDetectionEnabledState.value == enabled) {
                return
            }

            sharedPreferences
                .edit()
                .putBoolean(PreferenceConstants.CUBE_DETECTION_ENABLED, enabled)
                .apply()

            cubeDetectionEnabledState.value = enabled
        }

        override fun getRfidUid(city: com.robolig.controller.domain.model.City): String? {
            return sharedPreferences.getString(PreferenceConstants.RFID_UID_CITY_PREFIX + city.name, null)
        }

        override fun setRfidUid(
            city: com.robolig.controller.domain.model.City,
            uid: String?,
        ) {
            if (uid == null) {
                sharedPreferences.edit().remove(PreferenceConstants.RFID_UID_CITY_PREFIX + city.name).apply()
            } else {
                sharedPreferences.edit().putString(PreferenceConstants.RFID_UID_CITY_PREFIX + city.name, uid).apply()
            }
        }
    }
