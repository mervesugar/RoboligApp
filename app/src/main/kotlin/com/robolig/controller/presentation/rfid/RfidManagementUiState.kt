package com.robolig.controller.presentation.rfid

import com.robolig.controller.domain.model.City

data class RfidManagementUiState(
    val cityUids: Map<City, String> = City.entries.associateWith { "" },
    val cityErrors: Map<City, String?> = City.entries.associateWith { null },
    val duplicateError: String? = null,
    val saveStatusMessage: String? = null,
    val areFiveRecordsComplete: Boolean = false,
    val isReadyToSendToRobot: Boolean = false,
    val canSendToRobot: Boolean = false,
    val isSendToRobotEnabled: Boolean = false,
)
