package com.robolig.controller.presentation.rfid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robolig.controller.communication.CommunicationManager
import com.robolig.controller.communication.CommunicationState
import com.robolig.controller.domain.model.City
import com.robolig.controller.domain.model.ConnectionState
import com.robolig.controller.domain.model.RfidUid
import com.robolig.controller.domain.rfid.RfidSyncManager
import com.robolig.controller.domain.rfid.RfidSyncStage
import com.robolig.controller.domain.rfid.RfidSyncState
import com.robolig.controller.utils.ControllerPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class SyncStateInfo(
    val isSyncing: Boolean,
    val progressMessage: String?,
    val errorMessage: String?,
    val failedStage: RfidSyncStage?,
)

private object RfidVmHelper {
    fun computeSendButtonEnabled(
        state: RfidManagementUiState,
        commState: CommunicationState?,
        syncState: RfidSyncState,
        preferences: ControllerPreferences,
    ): Boolean {
        val isUsbConnected = commState?.connectionState == ConnectionState.SERIAL_OPEN
        val isHeartbeatHealthy = commState?.safety?.heartbeatHealthy == true
        val isEstopActive = commState?.safety?.emergencyStopLatched == true
        val isSyncActive = isSyncInProgress(syncState)
        val isSavedMatch = isSavedMatchingCurrentInput(state, preferences)

        return state.areFiveRecordsComplete &&
            isSavedMatch &&
            isUsbConnected &&
            isHeartbeatHealthy &&
            !isEstopActive &&
            !isSyncActive
    }

    fun isSyncInProgress(syncState: RfidSyncState): Boolean =
        syncState is RfidSyncState.SendingBegin ||
            syncState is RfidSyncState.WaitingBeginAck ||
            syncState is RfidSyncState.SendingItem ||
            syncState is RfidSyncState.WaitingItemAck ||
            syncState is RfidSyncState.SendingCommit ||
            syncState is RfidSyncState.WaitingCommitAck ||
            syncState is RfidSyncState.Validating

    fun isSavedMatchingCurrentInput(
        state: RfidManagementUiState,
        preferences: ControllerPreferences,
    ): Boolean {
        return if (!state.areFiveRecordsComplete) {
            false
        } else {
            City.entries.all { city ->
                val inputStr = state.cityUids[city]
                val model = if (!inputStr.isNullOrBlank()) RfidUid.create(inputStr).getOrNull() else null
                val prefVal = preferences.getRfidUid(city)
                model != null && prefVal == model.displayUid
            }
        }
    }

    fun mapSyncState(syncState: RfidSyncState): SyncStateInfo =
        when (syncState) {
            RfidSyncState.Idle -> SyncStateInfo(false, null, null, null)
            RfidSyncState.Validating -> SyncStateInfo(true, "RFID tablosu doğrulanıyor...", null, null)
            RfidSyncState.SendingBegin, RfidSyncState.WaitingBeginAck ->
                SyncStateInfo(true, "BEGIN paketi gönderiliyor...", null, null)
            is RfidSyncState.SendingItem ->
                SyncStateInfo(true, "${syncState.recordIndex + 1}/5 kayıt robota gönderiliyor...", null, null)
            is RfidSyncState.WaitingItemAck ->
                SyncStateInfo(true, "${syncState.recordIndex + 1}/5 kayıt onay bekleniyor...", null, null)
            RfidSyncState.SendingCommit, RfidSyncState.WaitingCommitAck ->
                SyncStateInfo(true, "Robot onayı bekleniyor (COMMIT)...", null, null)
            RfidSyncState.Success ->
                SyncStateInfo(false, "RFID tablosu robota başarıyla aktarıldı!", null, null)
            is RfidSyncState.Failed ->
                SyncStateInfo(false, null, "Aktarım başarısız: ${syncState.errorMessage}", syncState.stage)
            RfidSyncState.Disconnected ->
                SyncStateInfo(false, null, "Bağlantı koptu.", null)
        }

    fun extractValidModels(
        state: RfidManagementUiState,
        errors: Map<City, String?>,
    ): Map<City, RfidUid> {
        val validModels = mutableMapOf<City, RfidUid>()
        for (city in City.entries) {
            if (errors[city] == null && !state.cityUids[city].isNullOrBlank()) {
                RfidUid.create(state.cityUids[city]!!).getOrNull()?.let {
                    validModels[city] = it
                }
            }
        }
        return validModels
    }

    fun resetDuplicateErrors(errors: MutableMap<City, String?>) {
        for (city in City.entries) {
            if (errors[city] == "Duplicate UID" || errors[city] == "Tekrarlanan (Duplicate) UID") {
                errors[city] = null
            }
        }
    }

    fun markDuplicateErrors(
        models: Map<City, RfidUid>,
        errors: MutableMap<City, String?>,
    ): Boolean {
        var hasDuplicate = false
        for (c1 in models.keys) {
            for (c2 in models.keys) {
                if (c1 != c2 && models[c1] == models[c2]) {
                    errors[c1] = "Tekrarlanan (Duplicate) UID"
                    errors[c2] = "Tekrarlanan (Duplicate) UID"
                    hasDuplicate = true
                }
            }
        }
        return hasDuplicate
    }
}

@HiltViewModel
class RfidManagementViewModel
    @Inject
    constructor(
        private val preferences: ControllerPreferences,
        private val communicationManager: CommunicationManager?,
        private val rfidSyncManager: RfidSyncManager?,
    ) : ViewModel() {
        constructor(preferences: ControllerPreferences) : this(preferences, null, null)

        private val _uiState = MutableStateFlow(RfidManagementUiState())
        val uiState: StateFlow<RfidManagementUiState> = _uiState.asStateFlow()

        init {
            loadUids()
            observeSyncAndCommunication()
        }

        private fun observeSyncAndCommunication() {
            val commFlow = communicationManager?.state
            val syncFlow = rfidSyncManager?.syncState

            if (commFlow != null && syncFlow != null) {
                viewModelScope.launch {
                    combine(commFlow, syncFlow) { commState, syncState ->
                        Pair(commState, syncState)
                    }.collectLatest { (commState, syncState) ->
                        updateStateWithSyncAndComm(commState, syncState)
                    }
                }
            }
        }

        private fun updateStateWithSyncAndComm(
            commState: CommunicationState,
            syncState: RfidSyncState,
        ) {
            _uiState.update { state ->
                val sendEnabled = RfidVmHelper.computeSendButtonEnabled(
                    state = state,
                    commState = commState,
                    syncState = syncState,
                    preferences = preferences,
                )
                val info = RfidVmHelper.mapSyncState(syncState)

                state.copy(
                    isSendToRobotEnabled = sendEnabled,
                    isSyncing = info.isSyncing,
                    syncProgressMessage = info.progressMessage,
                    syncErrorMessage = info.errorMessage,
                    syncFailedStage = info.failedStage,
                )
            }
        }

        fun loadUids() {
            val loadedUids = mutableMapOf<City, String>()
            val loadedErrors = mutableMapOf<City, String?>()

            for (city in City.entries) {
                val prefValue = preferences.getRfidUid(city)
                if (!prefValue.isNullOrBlank()) {
                    val result = RfidUid.create(prefValue)
                    if (result.isSuccess) {
                        loadedUids[city] = result.getOrNull()!!.displayUid
                        loadedErrors[city] = null
                    } else {
                        loadedUids[city] = prefValue
                        loadedErrors[city] = "Bozuk kayıtlı veri"
                    }
                } else {
                    loadedUids[city] = ""
                    loadedErrors[city] = null
                }
            }

            _uiState.update {
                it.copy(
                    cityUids = loadedUids,
                    cityErrors = loadedErrors,
                    saveStatusMessage = null,
                )
            }
            validateAllUniqueness()
        }

        fun onUidChanged(
            city: City,
            uid: String,
        ) {
            _uiState.update { state ->
                val newUids = state.cityUids.toMutableMap()
                newUids[city] = uid
                val newErrors = state.cityErrors.toMutableMap()

                if (uid.isBlank()) {
                    newErrors[city] = null
                } else {
                    val result = RfidUid.create(uid)
                    newErrors[city] = if (result.isFailure) "Geçersiz UID" else null
                }

                state.copy(cityUids = newUids, cityErrors = newErrors, saveStatusMessage = null)
            }
            validateAllUniqueness()
        }

        private fun validateAllUniqueness() {
            _uiState.update { state ->
                val newErrors = state.cityErrors.toMutableMap()
                val validModels = RfidVmHelper.extractValidModels(state, newErrors)

                RfidVmHelper.resetDuplicateErrors(newErrors)
                val hasDuplicate = RfidVmHelper.markDuplicateErrors(validModels, newErrors)

                val duplicateMsg = if (hasDuplicate) "Tekrarlanan UID tespit edildi." else null
                val fiveComplete = City.entries.all { city ->
                    !state.cityUids[city].isNullOrBlank() && newErrors[city] == null
                }

                val draftState = state.copy(cityErrors = newErrors, areFiveRecordsComplete = fiveComplete)
                val sendEnabled = RfidVmHelper.computeSendButtonEnabled(
                    state = draftState,
                    commState = communicationManager?.state?.value,
                    syncState = rfidSyncManager?.syncState?.value ?: RfidSyncState.Idle,
                    preferences = preferences,
                )

                draftState.copy(
                    duplicateError = duplicateMsg,
                    isReadyToSendToRobot = sendEnabled,
                    canSendToRobot = sendEnabled,
                    isSendToRobotEnabled = sendEnabled,
                )
            }
        }

        fun clearFields() {
            _uiState.update {
                it.copy(
                    cityUids = City.entries.associateWith { "" },
                    cityErrors = City.entries.associateWith { null },
                    duplicateError = null,
                    saveStatusMessage = null,
                    areFiveRecordsComplete = false,
                    isReadyToSendToRobot = false,
                    canSendToRobot = false,
                    isSendToRobotEnabled = false,
                )
            }
        }

        fun saveToTablet() {
            val state = _uiState.value
            val hasErrors = state.cityErrors.values.any { it != null }
            val hasEmpty = state.cityUids.values.any { it.isBlank() }

            if (!hasErrors && !hasEmpty && state.areFiveRecordsComplete) {
                for (city in City.entries) {
                    val uidModel = RfidUid.create(state.cityUids[city]!!).getOrNull()
                    if (uidModel != null) {
                        preferences.setRfidUid(city, uidModel.displayUid)
                    }
                }
                _uiState.update { it.copy(saveStatusMessage = "Başarıyla kaydedildi.") }
                validateAllUniqueness()
            } else {
                _uiState.update { it.copy(saveStatusMessage = "Tüm alanlar geçerli bir UID ile doldurulmalıdır.") }
            }
        }

        fun sendToRobot() {
            val state = _uiState.value
            if (state.areFiveRecordsComplete) {
                val cityMap = mutableMapOf<City, RfidUid>()
                for (city in City.entries) {
                    val text = state.cityUids[city]
                    if (!text.isNullOrBlank()) {
                        RfidUid.create(text).getOrNull()?.let { cityMap[city] = it }
                    }
                }
                if (cityMap.size == 5) {
                    rfidSyncManager?.startSync(cityMap)
                }
            }
        }

        fun retrySync() {
            rfidSyncManager?.resetState()
            sendToRobot()
        }
    }
