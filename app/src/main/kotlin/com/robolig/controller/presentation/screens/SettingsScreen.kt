@file:Suppress("FunctionName")

package com.robolig.controller.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.robolig.controller.core.LogLevel
import com.robolig.controller.domain.model.RobotState
import com.robolig.controller.presentation.components.RoboligModeButton
import com.robolig.controller.presentation.components.RoboligToggleButton
import com.robolig.controller.presentation.theme.RoboligTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("LongMethod", "LongParameterList")
fun SettingsScreen(
    robotState: RobotState,
    onBackToDrive: () -> Unit,
    onVideoStreamUrlChanged: (String) -> Unit,
    onLogLevelChanged: (LogLevel) -> Unit,
    onShowPacketsOverlayChanged: (Boolean) -> Unit,
    onUseDeviceCameraChanged: (Boolean) -> Unit,
    onCubeDetectionChanged: (Boolean) -> Unit,
    onRefreshStatus: () -> Unit,
    onNavigateToRfidManagement: () -> Unit,
) {
    val spacing = RoboligTheme.spacing
    var streamUrl by
        rememberSaveable(robotState.camera.streamUrl) {
            mutableStateOf(robotState.camera.streamUrl)
        }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical,
                    ),
                ),
    ) {
        val isWideLayout = maxWidth >= 900.dp

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.page, vertical = spacing.page),
            verticalArrangement = Arrangement.spacedBy(spacing.section),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Control-channel configuration, diagnostics, and operator preferences.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsCard(
                title = "Video Stream",
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("MJPEG URL") },
                    singleLine = true,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.panel),
                    verticalArrangement = Arrangement.spacedBy(spacing.panel),
                ) {
                    RoboligModeButton(
                        label = "Apply",
                        selected = false,
                        onClick = { onVideoStreamUrlChanged(streamUrl) },
                    )
                    RoboligModeButton(label = "Refresh", selected = false, onClick = onRefreshStatus)
                    RoboligModeButton(label = "Back", selected = false, onClick = onBackToDrive)
                }
            }

            SettingsCard(
                title = "Camera Source",
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text =
                        "When enabled, the operator panel shows frames from this tablet's " +
                            "back camera instead of the MJPEG stream.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.panel),
                    verticalArrangement = Arrangement.spacedBy(spacing.panel),
                ) {
                    RoboligToggleButton(
                        label = "Use Device Camera",
                        checked = robotState.useDeviceCamera,
                        onToggle = onUseDeviceCameraChanged,
                    )
                }
            }

            SettingsCard(
                title = "Cube Detection",
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text =
                        "Shape-only detector runs on the active camera feed. Targets near-square " +
                            "quadrilaterals within the 9 × 9 cm cube size range at typical " +
                            "operating distances. Color is not used — the detector works on " +
                            "unknown colors.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.panel),
                    verticalArrangement = Arrangement.spacedBy(spacing.panel),
                ) {
                    RoboligToggleButton(
                        label = "Detect Cubes",
                        checked = robotState.cubeDetectionEnabled,
                        onToggle = onCubeDetectionChanged,
                    )
                }
            }

            SettingsCard(
                title = "Display",
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text =
                        "Overlay the live packet counter on the center of every control " +
                            "screen so you can verify the radio link at a glance.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.panel),
                    verticalArrangement = Arrangement.spacedBy(spacing.panel),
                ) {
                    RoboligToggleButton(
                        label = "Show Packets",
                        checked = robotState.showPacketsOverlay,
                        onToggle = onShowPacketsOverlayChanged,
                    )
                }
            }

            SettingsCard(
                title = "RFID Yönetimi",
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Manage RFID UIDs for the 5 fixed city models.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.panel),
                    verticalArrangement = Arrangement.spacedBy(spacing.panel),
                ) {
                    RoboligModeButton(
                        label = "RFID Yönetimi",
                        selected = false,
                        onClick = onNavigateToRfidManagement,
                    )
                }
            }

            if (isWideLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.section),
                ) {
                    SettingsCard(
                        title = "Logging Level",
                        modifier = Modifier.weight(0.9f),
                    ) {
                        LoggingLevelButtons(
                            selectedLevel = robotState.diagnostics.logLevel,
                            onLogLevelChanged = onLogLevelChanged,
                        )
                    }
                    SettingsCard(
                        title = "Controller Diagnostics",
                        modifier = Modifier.weight(1.1f),
                    ) {
                        DiagnosticsGrid(robotState = robotState)
                    }
                }
            } else {
                SettingsCard(
                    title = "Logging Level",
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LoggingLevelButtons(
                        selectedLevel = robotState.diagnostics.logLevel,
                        onLogLevelChanged = onLogLevelChanged,
                    )
                }
                SettingsCard(
                    title = "Controller Diagnostics",
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DiagnosticsGrid(robotState = robotState)
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                content()
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoggingLevelButtons(
    selectedLevel: LogLevel,
    onLogLevelChanged: (LogLevel) -> Unit,
) {
    val spacing = RoboligTheme.spacing
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.panel),
        verticalArrangement = Arrangement.spacedBy(spacing.panel),
    ) {
        LogLevel.entries.forEach { logLevel ->
            RoboligModeButton(
                label = logLevel.name,
                selected = selectedLevel == logLevel,
                onClick = { onLogLevelChanged(logLevel) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiagnosticsGrid(robotState: RobotState) {
    val diagnostics = robotState.diagnostics
    val spacing = RoboligTheme.spacing

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.section),
        verticalArrangement = Arrangement.spacedBy(spacing.section),
    ) {
        DiagnosticsMetricTile(label = "USB Packets Sent", value = diagnostics.packetsSent.toString())
        DiagnosticsMetricTile(label = "USB Bytes Sent", value = diagnostics.bytesSent.toString())
        DiagnosticsMetricTile(label = "USB Bytes Received", value = diagnostics.bytesReceived.toString())
        DiagnosticsMetricTile(label = "Queue Depth", value = diagnostics.queuedPackets.toString())
        DiagnosticsMetricTile(label = "Invalid Packets", value = diagnostics.invalidPackets.toString())
        DiagnosticsMetricTile(label = "Dropped Packets", value = diagnostics.droppedPackets.toString())
        DiagnosticsMetricTile(label = "Reconnect Attempts", value = diagnostics.reconnectAttempts.toString())
    }
}

@Composable
private fun DiagnosticsMetricTile(
    label: String,
    value: String,
) {
    Column(
        modifier = Modifier.widthIn(min = 150.dp, max = 220.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
