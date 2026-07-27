@file:Suppress("FunctionName")

package com.robolig.controller.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.robolig.controller.core.ProtocolConstants
import com.robolig.controller.domain.model.RobotMode
import com.robolig.controller.domain.model.RobotState
import com.robolig.controller.presentation.theme.RoboligTheme
import com.robolig.controller.protocol.ArmControlPayload
import com.robolig.controller.protocol.PacketFlags
import com.robolig.controller.protocol.PacketType
import com.robolig.controller.protocol.VehicleControlPayload
import com.robolig.controller.protocol.readTimestamp
import com.robolig.controller.video.DeviceCameraViewModel

enum class RailActionStyle {
    STANDARD,
    TOGGLE,
    MOMENTARY,
}

data class RailAction(
    val label: String,
    val style: RailActionStyle = RailActionStyle.STANDARD,
    val checked: Boolean = false,
    val onClick: (() -> Unit)? = null,
    val onPressedChanged: ((Boolean) -> Unit)? = null,
)

data class RobotControlFrameActions(
    val onModeSelected: (RobotMode) -> Unit,
    val onEmergencyStop: () -> Unit,
    val onEmergencyStopReset: () -> Unit,
    val onOpenSettings: () -> Unit,
)

@Composable
@Suppress("LongMethod")
fun RobotControlScaffold(
    robotState: RobotState,
    selectedMode: RobotMode,
    sideActions: List<RailAction>,
    frameActions: RobotControlFrameActions,
    modifier: Modifier = Modifier,
    rightRailContent: (@Composable ColumnScope.() -> Unit)? = null,
    overlayContent: @Composable BoxScope.() -> Unit,
) {
    val spacing = RoboligTheme.spacing

    DeviceCameraBinder(useDeviceCamera = robotState.useDeviceCamera)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                )
                .padding(horizontal = spacing.page, vertical = spacing.page),
        verticalArrangement = Arrangement.spacedBy(spacing.section),
    ) {
        TelemetryBar(
            robotState = robotState,
            selectedMode = selectedMode,
            onModeSelected = frameActions.onModeSelected,
            onEmergencyStop = frameActions.onEmergencyStop,
            onEmergencyStopReset = frameActions.onEmergencyStopReset,
            onOpenSettings = frameActions.onOpenSettings,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            CameraView(
                cameraState = robotState.camera,
                modifier = Modifier.fillMaxSize(),
            )
            overlayContent()

            if (robotState.showPacketsOverlay) {
                OutboundPacketOverlay(
                    bytes = robotState.diagnostics.lastOutboundBytes,
                    secondaryBytes = robotState.diagnostics.lastOutboundSecondaryBytes,
                    packetsSent = robotState.diagnostics.packetsSent,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            RailContainer(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = spacing.section, top = spacing.section, bottom = spacing.section)
                        .width(spacing.railWidth),
            ) {
                sideActions.forEach { action ->
                    when (action.style) {
                        RailActionStyle.STANDARD ->
                            RoboligModeButton(
                                label = action.label,
                                selected = action.checked,
                                onClick = action.onClick ?: {},
                                modifier = Modifier.fillMaxWidth(),
                            )
                        RailActionStyle.TOGGLE ->
                            RoboligToggleButton(
                                label = action.label,
                                checked = action.checked,
                                onToggle = { _ -> action.onClick?.invoke() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        RailActionStyle.MOMENTARY ->
                            RoboligMomentaryButton(
                                label = action.label,
                                onPressedChanged = action.onPressedChanged ?: {},
                                modifier = Modifier.fillMaxWidth(),
                            )
                    }
                }
            }

            if (rightRailContent != null) {
                RailContainer(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = spacing.section, top = spacing.section, bottom = spacing.section)
                            .width(spacing.railWidth),
                    content = rightRailContent,
                )
            }
        }
    }
}

@Composable
private fun RailContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun OutboundPacketOverlay(
    bytes: ByteArray?,
    secondaryBytes: ByteArray?,
    packetsSent: Long,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(min = 460.dp, max = 520.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            if (bytes == null || bytes.size != ProtocolConstants.PACKET_SIZE_BYTES) {
                Text(
                    text = "OUTBOUND PACKET",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Waiting for the scheduler to build a packet…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "packets sent: $packetsSent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            PacketHeader(bytes = bytes, packetsSent = packetsSent)
            DecodedPayload(bytes = bytes)
            PacketHexDump(bytes = bytes)
            PacketFlagSummary(bytes = bytes)

            if (secondaryBytes != null && secondaryBytes.size == ProtocolConstants.PACKET_SIZE_BYTES) {
                SecondaryPacketLine(bytes = secondaryBytes)
            }
        }
    }
}

@Composable
private fun SecondaryPacketLine(bytes: ByteArray) {
    val type = PacketType.fromWireValue(bytes[1].toInt().and(0xFF))
    val sequence = bytes[2].toInt().and(0xFF)
    val label =
        when (type) {
            PacketType.VEHICLE_CONTROL -> "drive joystick"
            PacketType.ARM_CONTROL -> "arm joystick"
            PacketType.EMERGENCY_STOP -> "e-stop"
            PacketType.HEARTBEAT -> "heartbeat"
            PacketType.TELEMETRY_REQUEST -> "telemetry request"
            PacketType.TELEMETRY_RESPONSE -> "telemetry response"
            PacketType.PTZ_CONTROL -> "ptz control"
            PacketType.RFID_CONFIG_BEGIN -> "rfid begin"
            PacketType.RFID_CONFIG_ITEM -> "rfid item"
            PacketType.RFID_CONFIG_COMMIT -> "rfid commit"
            PacketType.RFID_CONFIG_ACK -> "rfid ack"
            PacketType.RFID_CONFIG_NACK -> "rfid nack"
            null -> "other"
        }
    val summary =
        when (type) {
            PacketType.VEHICLE_CONTROL -> {
                val payloadBytes = bytes.copyOfRange(4, 4 + ProtocolConstants.PAYLOAD_SIZE_BYTES)
                val p = VehicleControlPayload.fromPayload(payloadBytes)
                "moveX=${formatSigned(p.moveX)} moveY=${formatSigned(p.moveY)} rot=${formatSigned(p.rotation)} " +
                    "throttle=${formatSigned(p.throttle)} brake=${formatSigned(p.brake)} boost=${formatSigned(p.boost)}"
            }
            PacketType.ARM_CONTROL -> {
                val p = ArmControlPayload.fromPayload(bytes.copyOfRange(4, 4 + ProtocolConstants.PAYLOAD_SIZE_BYTES))
                "shoulder=${p.shoulder}° elbow=${p.elbow}° wristP=${p.wristPitch}° wristR=${p.wristRoll}° " +
                    "gripRot=${p.gripperRotation}° gripper=${if (p.gripper == 0) "CLOSED" else "OPEN(${p.gripper})"}"
            }
            else -> "no payload"
        }
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = "$label · ${type?.name ?: "UNKNOWN"} #${"%03d".format(sequence)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PacketHeader(
    bytes: ByteArray,
    packetsSent: Long,
) {
    val packetType = PacketType.fromWireValue(bytes[1].toInt().and(0xFF))
    val sequence = bytes[2].toInt().and(0xFF)
    val timestampMs = readTimestamp(bytes)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${packetType?.name ?: "UNKNOWN"} #${"%03d".format(sequence)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "sent=$packetsSent",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = "timestamp=${timestampMs}ms",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DecodedPayload(bytes: ByteArray) {
    val payload = bytes.copyOfRange(4, 4 + ProtocolConstants.PAYLOAD_SIZE_BYTES)
    val text =
        when (PacketType.fromWireValue(bytes[1].toInt().and(0xFF))) {
            PacketType.VEHICLE_CONTROL -> decodeVehicle(payload)
            PacketType.ARM_CONTROL -> decodeArm(payload)
            PacketType.PTZ_CONTROL -> "PTZ_CONTROL (no decoder wired in UI)"
            PacketType.TELEMETRY_REQUEST -> "no payload — requests a telemetry response from the bridge"
            PacketType.TELEMETRY_RESPONSE -> "TELEMETRY_RESPONSE (inbound only; should never appear here)"
            PacketType.EMERGENCY_STOP -> "no payload — latches E-stop on the bridge"
            PacketType.HEARTBEAT -> "no payload — keeps the watchdog happy"
            PacketType.RFID_CONFIG_BEGIN -> "RFID_CONFIG_BEGIN"
            PacketType.RFID_CONFIG_ITEM -> "RFID_CONFIG_ITEM"
            PacketType.RFID_CONFIG_COMMIT -> "RFID_CONFIG_COMMIT"
            PacketType.RFID_CONFIG_ACK -> "RFID_CONFIG_ACK"
            PacketType.RFID_CONFIG_NACK -> "RFID_CONFIG_NACK"
            null -> "unknown packet type 0x${"%02X".format(bytes[1].toInt().and(0xFF))}"
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        ),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private fun decodeVehicle(payload: ByteArray): String {
    val p = VehicleControlPayload.fromPayload(payload)
    return buildString {
        append("moveX=").append(formatSigned(p.moveX)).append("  ")
        append("moveY=").append(formatSigned(p.moveY)).append("  ")
        append("rot=").append(formatSigned(p.rotation)).append('\n')
        append("throttle=").append(formatSigned(p.throttle)).append("  ")
        append("brake=").append(formatSigned(p.brake)).append("  ")
        append("boost=").append(formatSigned(p.boost))
    }
}

private fun decodeArm(payload: ByteArray): String {
    val p = ArmControlPayload.fromPayload(payload)
    return buildString {
        append("shoulder=").append(p.shoulder).append("°  ")
        append("elbow=").append(p.elbow).append("°  ")
        append("wristP=").append(p.wristPitch).append("°\n")
        append("wristR=").append(p.wristRoll).append("°  ")
        append("gripRot=").append(p.gripperRotation).append("°  ")
        append("gripper=")
        append(if (p.gripper == 0) "CLOSED" else "OPEN(${p.gripper})")
    }
}

private fun formatSigned(value: Int): String =
    if (value > 0) "+$value" else value.toString()

@Composable
private fun PacketHexDump(bytes: ByteArray) {
    val rows = bytes.toList().chunked(8)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { row ->
            val hex = row.joinToString(" ") { "%02X".format(it.toInt().and(0xFF)) }
            Text(
                text = hex,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun PacketFlagSummary(bytes: ByteArray) {
    val flags = PacketFlags.fromWireValue(bytes[3].toInt().and(0xFF))
    val active = mutableListOf<String>()
    if (flags.emergencyStop) active += "E-STOP"
    if (flags.precisionMode) active += "PRECISION"
    if (flags.armLocked) active += "ARM-LOCK"
    if (flags.vehicleLocked) active += "VEH-LOCK"
    if (flags.autoMode) active += "AUTO"
    val flagLine = if (active.isEmpty()) "flags=none" else "flags=" + active.joinToString("|")
    Text(
        text = flagLine,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DeviceCameraBinder(useDeviceCamera: Boolean) {
    val viewModel: DeviceCameraViewModel = hiltViewModel()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val pendingBindState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var pendingBind by pendingBindState
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingBind) {
            viewModel.bind(lifecycleOwner)
        }
        pendingBind = false
    }
    LaunchedEffect(useDeviceCamera) {
        if (!useDeviceCamera) {
            viewModel.unbind()
            pendingBind = false
            return@LaunchedEffect
        }
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.bind(lifecycleOwner)
        } else {
            pendingBind = true
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.unbind() }
    }
}
