@file:Suppress("EmptyFunctionBlock")

package com.robolig.controller.presentation.rfid

import com.robolig.controller.core.LogLevel
import com.robolig.controller.domain.model.City
import com.robolig.controller.utils.ControllerPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeControllerPreferences : ControllerPreferences {
    private val rfidUids = mutableMapOf<City, String>()

    override val videoStreamUrl: StateFlow<String> = MutableStateFlow("")
    override val logLevel: StateFlow<LogLevel> = MutableStateFlow(LogLevel.DEBUG)
    override val showPacketsOverlay: StateFlow<Boolean> = MutableStateFlow(false)
    override val useDeviceCamera: StateFlow<Boolean> = MutableStateFlow(false)
    override val cubeDetectionEnabled: StateFlow<Boolean> = MutableStateFlow(false)

    override fun updateVideoStreamUrl(url: String) {}

    override fun updateLogLevel(level: LogLevel) {}

    override fun updateShowPacketsOverlay(enabled: Boolean) {}

    override fun updateUseDeviceCamera(enabled: Boolean) {}

    override fun updateCubeDetectionEnabled(enabled: Boolean) {}

    override fun getRfidUid(city: City): String? {
        return rfidUids[city]
    }

    override fun setRfidUid(
        city: City,
        uid: String?,
    ) {
        if (uid == null) {
            rfidUids.remove(city)
        } else {
            rfidUids[city] = uid
        }
    }
}

class RfidManagementViewModelTest {
    @Test
    fun `cities are exactly SINOP, NIGDE, TOKAT, AYDIN, ELAZIG`() {
        val expectedCities = listOf(City.SINOP, City.NIGDE, City.TOKAT, City.AYDIN, City.ELAZIG)
        assertEquals(expectedCities, City.entries)
        assertEquals(5, City.entries.size)
    }

    @Test
    fun `viewModel loads initial values from preferences`() {
        val prefs = FakeControllerPreferences()
        prefs.setRfidUid(City.SINOP, "11223344")

        val viewModel = RfidManagementViewModel(prefs)
        val state = viewModel.uiState.value

        assertEquals("11223344", state.cityUids[City.SINOP])
        assertEquals("", state.cityUids[City.NIGDE])
    }

    @Test
    fun `entering valid uid does not save until saveToTablet is called`() {
        val prefs = FakeControllerPreferences()
        val viewModel = RfidManagementViewModel(prefs)

        viewModel.onUidChanged(City.SINOP, "1a:2B-3C:4d")
        // Fill others to make it valid for save
        viewModel.onUidChanged(City.NIGDE, "AABBCCDD")
        viewModel.onUidChanged(City.TOKAT, "11112222")
        viewModel.onUidChanged(City.AYDIN, "33334444")
        viewModel.onUidChanged(City.ELAZIG, "55556666")

        val state = viewModel.uiState.value
        assertNull(state.cityErrors[City.SINOP])
        assertNull(prefs.getRfidUid(City.SINOP))

        viewModel.saveToTablet()
        assertEquals("1A2B3C4D", prefs.getRfidUid(City.SINOP))
    }

    @Test
    fun `entering invalid uid sets error and prevents save`() {
        val prefs = FakeControllerPreferences()
        val viewModel = RfidManagementViewModel(prefs)

        viewModel.onUidChanged(City.SINOP, "invalid")

        val state = viewModel.uiState.value
        assertTrue(state.cityErrors[City.SINOP]?.isNotEmpty() == true)

        viewModel.saveToTablet()
        assertNull(prefs.getRfidUid(City.SINOP))
    }

    @Test
    fun `entering duplicate uid sets error and prevents save`() {
        val prefs = FakeControllerPreferences()
        prefs.setRfidUid(City.NIGDE, "11223344")
        val viewModel = RfidManagementViewModel(prefs) // will load NIGDE = 11223344

        viewModel.onUidChanged(City.SINOP, "11 22 33 44") // duplicate, formatting shouldn't matter

        val state = viewModel.uiState.value
        assertTrue(state.cityErrors[City.SINOP]?.contains("Duplicate") == true)
        assertTrue(state.cityErrors[City.NIGDE]?.contains("Duplicate") == true)

        viewModel.saveToTablet()
        assertNull(prefs.getRfidUid(City.SINOP)) // should not be saved
    }

    @Test
    fun `missing UID prevents save and shows error`() {
        val prefs = FakeControllerPreferences()
        val viewModel = RfidManagementViewModel(prefs)

        viewModel.onUidChanged(City.SINOP, "11223344")
        viewModel.onUidChanged(City.NIGDE, "AABBCCDD")
        viewModel.onUidChanged(City.TOKAT, "11112222")
        viewModel.onUidChanged(City.AYDIN, "33334444")
        // ELAZIG is missing

        viewModel.saveToTablet()
        assertNull(prefs.getRfidUid(City.SINOP))
        assertTrue(viewModel.uiState.value.saveStatusMessage?.contains("Tüm alanlar") == true)
    }

    @Test
    fun `five valid UIDs allow successful save and reload via preferences`() {
        val prefs = FakeControllerPreferences()
        val viewModel = RfidManagementViewModel(prefs)

        viewModel.onUidChanged(City.SINOP, "11223344")
        viewModel.onUidChanged(City.NIGDE, "22334455")
        viewModel.onUidChanged(City.TOKAT, "33445566")
        viewModel.onUidChanged(City.AYDIN, "44556677")
        viewModel.onUidChanged(City.ELAZIG, "55667788")

        assertTrue(viewModel.uiState.value.areFiveRecordsComplete)

        viewModel.saveToTablet()
        assertEquals("11223344", prefs.getRfidUid(City.SINOP))
        assertEquals("22334455", prefs.getRfidUid(City.NIGDE))
        assertEquals("33445566", prefs.getRfidUid(City.TOKAT))
        assertEquals("44556677", prefs.getRfidUid(City.AYDIN))
        assertEquals("55667788", prefs.getRfidUid(City.ELAZIG))

        val reloadedViewModel = RfidManagementViewModel(prefs)
        assertEquals("11223344", reloadedViewModel.uiState.value.cityUids[City.SINOP])
        assertTrue(reloadedViewModel.uiState.value.areFiveRecordsComplete)
    }

    @Test
    fun `corrupt preference data is handled safely without app crash`() {
        val prefs = FakeControllerPreferences()
        prefs.setRfidUid(City.SINOP, "CORRUPT_DATA")

        val viewModel = RfidManagementViewModel(prefs)
        val state = viewModel.uiState.value

        assertEquals("CORRUPT_DATA", state.cityUids[City.SINOP])
        assertEquals("Bozuk kayıtlı veri", state.cityErrors[City.SINOP])
        assertFalse(state.areFiveRecordsComplete)
    }

    @Test
    fun `send to robot button is disabled when communication or sync is missing`() {
        val prefs = FakeControllerPreferences()
        val viewModel = RfidManagementViewModel(prefs)

        val state = viewModel.uiState.value
        assertFalse(state.isSendToRobotEnabled)
        assertFalse(state.canSendToRobot)
        assertFalse(state.isReadyToSendToRobot)
    }

    @Test
    fun `clearing fields updates state`() {
        val prefs = FakeControllerPreferences()
        prefs.setRfidUid(City.SINOP, "11223344")
        val viewModel = RfidManagementViewModel(prefs)

        viewModel.clearFields()

        val state = viewModel.uiState.value
        assertNull(state.cityErrors[City.SINOP])
        assertEquals("", state.cityUids[City.SINOP])
    }
}
