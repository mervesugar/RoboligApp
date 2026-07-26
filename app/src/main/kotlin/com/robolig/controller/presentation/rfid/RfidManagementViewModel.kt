package com.robolig.controller.presentation.rfid

import androidx.lifecycle.ViewModel
import com.robolig.controller.domain.model.City
import com.robolig.controller.domain.model.RfidUid
import com.robolig.controller.utils.ControllerPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class RfidManagementViewModel
    @Inject
    constructor(
        private val preferences: ControllerPreferences,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(RfidManagementUiState())
        val uiState: StateFlow<RfidManagementUiState> = _uiState.asStateFlow()

        init {
            loadUids()
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
                    if (result.isFailure) {
                        newErrors[city] = "Geçersiz UID"
                    } else {
                        newErrors[city] = null
                    }
                }

                state.copy(cityUids = newUids, cityErrors = newErrors, saveStatusMessage = null)
            }
            validateAllUniqueness()
        }

        private fun validateAllUniqueness() {
            _uiState.update { state ->
                val newErrors = state.cityErrors.toMutableMap()

                val validModels = mutableMapOf<City, RfidUid>()
                for (city in City.entries) {
                    if (newErrors[city] == null && !state.cityUids[city].isNullOrBlank()) {
                        RfidUid.create(state.cityUids[city]!!).getOrNull()?.let {
                            validModels[city] = it
                        }
                    }
                }

                // Reset duplicate errors
                for (city in City.entries) {
                    if (newErrors[city] == "Duplicate UID" || newErrors[city] == "Tekrarlanan (Duplicate) UID") {
                        newErrors[city] = null
                    }
                }

                var hasDuplicate = false
                for (city1 in validModels.keys) {
                    for (city2 in validModels.keys) {
                        if (city1 != city2 && validModels[city1] == validModels[city2]) {
                            newErrors[city1] = "Tekrarlanan (Duplicate) UID"
                            newErrors[city2] = "Tekrarlanan (Duplicate) UID"
                            hasDuplicate = true
                        }
                    }
                }

                val duplicateMsg = if (hasDuplicate) "Tekrarlanan UID tespit edildi." else null
                val fiveComplete =
                    City.entries.all { city ->
                        val text = state.cityUids[city]
                        !text.isNullOrBlank() && newErrors[city] == null
                    }

                state.copy(
                    cityErrors = newErrors,
                    duplicateError = duplicateMsg,
                    areFiveRecordsComplete = fiveComplete,
                    isReadyToSendToRobot = false,
                    canSendToRobot = false,
                    isSendToRobotEnabled = false,
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

            if (hasErrors || hasEmpty || !state.areFiveRecordsComplete) {
                _uiState.update { it.copy(saveStatusMessage = "Tüm alanlar geçerli bir UID ile doldurulmalıdır.") }
                return
            }

            for (city in City.entries) {
                val uidModel = RfidUid.create(state.cityUids[city]!!).getOrNull()
                if (uidModel != null) {
                    preferences.setRfidUid(city, uidModel.displayUid)
                }
            }

            _uiState.update { it.copy(saveStatusMessage = "Başarıyla kaydedildi.") }
        }
    }
