@file:Suppress("FunctionName")

package com.robolig.controller.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.robolig.controller.domain.model.RobotMode
import com.robolig.controller.presentation.components.RobotControlFrameActions
import com.robolig.controller.presentation.screens.AboutScreen
import com.robolig.controller.presentation.screens.AutoScreen
import com.robolig.controller.presentation.screens.AutoScreenActions
import com.robolig.controller.presentation.screens.DriveScreen
import com.robolig.controller.presentation.screens.DriveScreenActions
import com.robolig.controller.presentation.screens.GripperScreen
import com.robolig.controller.presentation.screens.GripperScreenActions
import com.robolig.controller.presentation.screens.SettingsScreen
import com.robolig.controller.presentation.screens.ZiplineScreen
import com.robolig.controller.presentation.screens.ZiplineScreenActions
import com.robolig.controller.presentation.viewmodel.ArmViewModel
import com.robolig.controller.presentation.viewmodel.AutoViewModel
import com.robolig.controller.presentation.viewmodel.DriveViewModel
import com.robolig.controller.presentation.viewmodel.MainViewModel
import com.robolig.controller.presentation.viewmodel.SettingsViewModel

private const val SETTINGS_ROUTE = "settings"
private const val ABOUT_ROUTE = "about"
private const val RFID_MANAGEMENT_ROUTE = "rfid_management"

@Composable
@Suppress("LongMethod")
fun NavigationGraph() {
    val navController = rememberNavController()

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            restoreState = true
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = RobotMode.DRIVE.route,
    ) {
        composable(route = RobotMode.DRIVE.route) {
            val mainViewModel: MainViewModel = hiltViewModel()
            val driveViewModel: DriveViewModel = hiltViewModel()
            val robotState by mainViewModel.robotState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                mainViewModel.setRobotMode(RobotMode.DRIVE)
            }
            DriveScreen(
                robotState = robotState,
                actions =
                    DriveScreenActions(
                        frameActions =
                            RobotControlFrameActions(
                                onModeSelected = { mode -> navigateTo(mode.route) },
                                onEmergencyStop = mainViewModel::triggerEmergencyStop,
                                onEmergencyStopReset = mainViewModel::resetEmergencyStop,
                                onOpenSettings = { navigateTo(SETTINGS_ROUTE) },
                            ),
                        onDriveInputChanged = driveViewModel::updateDriveInput,
                        onBoostChanged = driveViewModel::setBoostEnabled,
                        onBrakeChanged = driveViewModel::setBrakeActive,
                    ),
            )
        }

        composable(route = RobotMode.GRIPPER.route) {
            val mainViewModel: MainViewModel = hiltViewModel()
            val driveViewModel: DriveViewModel = hiltViewModel()
            val armViewModel: ArmViewModel = hiltViewModel()
            val robotState by mainViewModel.robotState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                mainViewModel.setRobotMode(RobotMode.GRIPPER)
            }
            GripperScreen(
                robotState = robotState,
                actions =
                    GripperScreenActions(
                        frameActions =
                            RobotControlFrameActions(
                                onModeSelected = { mode -> navigateTo(mode.route) },
                                onEmergencyStop = mainViewModel::triggerEmergencyStop,
                                onEmergencyStopReset = mainViewModel::resetEmergencyStop,
                                onOpenSettings = { navigateTo(SETTINGS_ROUTE) },
                            ),
                        onDriveInputChanged = driveViewModel::updateDriveInput,
                        onPlanarInputChanged = armViewModel::updatePlanarInput,
                        onDepthInputChanged = armViewModel::updateDepthInput,
                        onWristRotationChanged = armViewModel::updateWristRotation,
                        onGripperOpenChanged = armViewModel::setGripperOpen,
                        onPrecisionModeChanged = armViewModel::setPrecisionMode,
                        onPresetActivated = armViewModel::activatePreset,
                    ),
            )
        }

        composable(route = RobotMode.ZIPLINE.route) {
            val mainViewModel: MainViewModel = hiltViewModel()
            val driveViewModel: DriveViewModel = hiltViewModel()
            val armViewModel: ArmViewModel = hiltViewModel()
            val robotState by mainViewModel.robotState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                mainViewModel.setRobotMode(RobotMode.ZIPLINE)
            }
            ZiplineScreen(
                robotState = robotState,
                actions =
                    ZiplineScreenActions(
                        frameActions =
                            RobotControlFrameActions(
                                onModeSelected = { mode -> navigateTo(mode.route) },
                                onEmergencyStop = mainViewModel::triggerEmergencyStop,
                                onEmergencyStopReset = mainViewModel::resetEmergencyStop,
                                onOpenSettings = { navigateTo(SETTINGS_ROUTE) },
                            ),
                        onDriveInputChanged = driveViewModel::updateDriveInput,
                        onPlanarInputChanged = armViewModel::updatePlanarInput,
                        onGripperOpenChanged = armViewModel::setGripperOpen,
                        onZiplineHeightChanged = armViewModel::updateZiplineHeight,
                    ),
            )
        }

        composable(route = RobotMode.AUTO.route) {
            val mainViewModel: MainViewModel = hiltViewModel()
            val autoViewModel: AutoViewModel = hiltViewModel()
            val robotState by mainViewModel.robotState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                mainViewModel.setRobotMode(RobotMode.AUTO)
            }
            AutoScreen(
                robotState = robotState,
                actions =
                    AutoScreenActions(
                        frameActions =
                            RobotControlFrameActions(
                                onModeSelected = { mode -> navigateTo(mode.route) },
                                onEmergencyStop = mainViewModel::triggerEmergencyStop,
                                onEmergencyStopReset = mainViewModel::resetEmergencyStop,
                                onOpenSettings = { navigateTo(SETTINGS_ROUTE) },
                            ),
                        onMissionPausedChanged = autoViewModel::setMissionPaused,
                        onAbortMission = autoViewModel::abortMission,
                    ),
            )
        }

        composable(route = SETTINGS_ROUTE) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val robotState by settingsViewModel.robotState.collectAsStateWithLifecycle()
            SettingsScreen(
                robotState = robotState,
                onBackToDrive = { navigateTo(RobotMode.DRIVE.route) },
                onVideoStreamUrlChanged = settingsViewModel::updateVideoStreamUrl,
                onLogLevelChanged = settingsViewModel::updateLogLevel,
                onShowPacketsOverlayChanged = settingsViewModel::toggleShowPacketsOverlay,
                onUseDeviceCameraChanged = settingsViewModel::toggleUseDeviceCamera,
                onCubeDetectionChanged = settingsViewModel::toggleCubeDetection,
                onRefreshStatus = settingsViewModel::refreshStatus,
                onNavigateToRfidManagement = { navigateTo(RFID_MANAGEMENT_ROUTE) },
            )
        }

        composable(route = RFID_MANAGEMENT_ROUTE) {
            com.robolig.controller.presentation.rfid.RfidManagementScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(route = ABOUT_ROUTE) {
            AboutScreen(
                onBackToDrive = { navigateTo(RobotMode.DRIVE.route) },
            )
        }
    }
}
