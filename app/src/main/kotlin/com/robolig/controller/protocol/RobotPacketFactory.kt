package com.robolig.controller.protocol

import com.robolig.controller.core.ProtocolConstants
import com.robolig.controller.domain.model.ArmState
import com.robolig.controller.domain.model.JointAngles
import com.robolig.controller.domain.model.RobotMode
import com.robolig.controller.domain.model.SafetyState
import com.robolig.controller.domain.model.VehicleState
import com.robolig.controller.utils.MonotonicClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class PacketSequenceGenerator
    @Inject
    constructor() {
        private var nextSequenceNumber = 0

        fun next(): Int {
            val current = nextSequenceNumber
            nextSequenceNumber = (nextSequenceNumber + 1) and 0xFF
            return current
        }
    }

@Singleton
class RobotPacketFactory
    @Inject
    constructor(
        private val clock: MonotonicClock,
        private val sequenceGenerator: PacketSequenceGenerator,
    ) {
        fun createVehicleControlPacket(
            vehicleState: VehicleState,
            robotMode: RobotMode,
            safetyState: SafetyState,
        ): Packet =
            Packet(
                type = PacketType.VEHICLE_CONTROL,
                sequenceNumber = sequenceGenerator.next(),
                flags =
                    buildFlags(
                        robotMode,
                        safetyState,
                        precisionMode = false,
                        armLocked = false,
                        vehicleLocked = vehicleState.locked,
                    ),
                payload =
                    VehicleControlPayload(
                        moveX = scaleSignedControl(vehicleState.translationInput.x),
                        moveY = scaleSignedControl(vehicleState.translationInput.y),
                        rotation = scaleSignedControl(vehicleState.rotationInput),
                        throttle = scaleUnsignedToSigned(vehicleState.throttlePercent),
                        brake = scaleUnsignedToSigned(vehicleState.brakePercent),
                        boost = if (vehicleState.boostEnabled) ProtocolConstants.MAX_SIGNED_CONTROL_VALUE else 0,
                    ).toPayloadBytes(),
                timestampMillis = timestamp(),
            )

        fun createArmControlPacket(
            armState: ArmState,
            jointAngles: JointAngles,
            robotMode: RobotMode,
            safetyState: SafetyState,
        ): Packet =
            Packet(
                type = PacketType.ARM_CONTROL,
                sequenceNumber = sequenceGenerator.next(),
                flags =
                    buildFlags(
                        robotMode = robotMode,
                        safetyState = safetyState,
                        precisionMode = armState.precisionModeEnabled,
                        armLocked = armState.locked,
                        vehicleLocked = false,
                    ),
                payload =
                    ArmControlPayload(
                        shoulder = jointAngles.shoulderDegrees,
                        elbow = jointAngles.elbowDegrees,
                        wristPitch = jointAngles.wristPitchDegrees,
                        wristRoll = jointAngles.wristRollDegrees,
                        gripperRotation = jointAngles.gripperRotationDegrees,
                        gripper = jointAngles.gripperCommand,
                    ).toPayloadBytes(),
                timestampMillis = timestamp(),
            )

        fun createTelemetryRequestPacket(
            robotMode: RobotMode,
            safetyState: SafetyState,
        ): Packet =
            Packet(
                type = PacketType.TELEMETRY_REQUEST,
                sequenceNumber = sequenceGenerator.next(),
                flags =
                    buildFlags(
                        robotMode,
                        safetyState,
                        precisionMode = false,
                        armLocked = false,
                        vehicleLocked = false,
                    ),
                timestampMillis = timestamp(),
            )

        fun createHeartbeatPacket(
            robotMode: RobotMode,
            safetyState: SafetyState,
        ): Packet =
            Packet(
                type = PacketType.HEARTBEAT,
                sequenceNumber = sequenceGenerator.next(),
                flags =
                    buildFlags(
                        robotMode,
                        safetyState,
                        precisionMode = false,
                        armLocked = false,
                        vehicleLocked = false,
                    ),
                timestampMillis = timestamp(),
            )

        fun createEmergencyStopPacket(robotMode: RobotMode): Packet =
            Packet(
                type = PacketType.EMERGENCY_STOP,
                sequenceNumber = sequenceGenerator.next(),
                flags =
                    buildFlags(
                        robotMode = robotMode,
                        safetyState = SafetyState(emergencyStopLatched = true),
                        precisionMode = false,
                        armLocked = true,
                        vehicleLocked = true,
                    ),
                timestampMillis = timestamp(),
            )

        fun createRfidConfigBeginPacket(
            robotMode: RobotMode,
            safetyState: SafetyState,
            beginPayload: RfidConfigBeginPayload,
        ): Packet =
            Packet(
                type = PacketType.RFID_CONFIG_BEGIN,
                sequenceNumber = sequenceGenerator.next(),
                flags =
                    buildFlags(
                        robotMode = robotMode,
                        safetyState = safetyState,
                        precisionMode = false,
                        armLocked = false,
                        vehicleLocked = false,
                    ),
                payload = beginPayload.toPayloadBytes(),
                timestampMillis = timestamp(),
            )

        fun createRfidConfigItemPacket(
            robotMode: RobotMode,
            safetyState: SafetyState,
            itemPayload: RfidConfigItemPayload,
        ): Packet =
            Packet(
                type = PacketType.RFID_CONFIG_ITEM,
                sequenceNumber = sequenceGenerator.next(),
                flags =
                    buildFlags(
                        robotMode = robotMode,
                        safetyState = safetyState,
                        precisionMode = false,
                        armLocked = false,
                        vehicleLocked = false,
                    ),
                payload = itemPayload.toPayloadBytes(),
                timestampMillis = timestamp(),
            )

        fun createRfidConfigCommitPacket(
            robotMode: RobotMode,
            safetyState: SafetyState,
            commitPayload: RfidConfigCommitPayload,
        ): Packet =
            Packet(
                type = PacketType.RFID_CONFIG_COMMIT,
                sequenceNumber = sequenceGenerator.next(),
                flags =
                    buildFlags(
                        robotMode = robotMode,
                        safetyState = safetyState,
                        precisionMode = false,
                        armLocked = false,
                        vehicleLocked = false,
                    ),
                payload = commitPayload.toPayloadBytes(),
                timestampMillis = timestamp(),
            )

        private fun timestamp(): Int = (clock.elapsedRealtimeMs().toInt() and ProtocolConstants.TIMESTAMP_MASK)
    }

private fun buildFlags(
    robotMode: RobotMode,
    safetyState: SafetyState,
    precisionMode: Boolean,
    armLocked: Boolean,
    vehicleLocked: Boolean,
): PacketFlags =
    PacketFlags(
        emergencyStop = safetyState.emergencyStopLatched,
        precisionMode = precisionMode,
        armLocked = armLocked,
        vehicleLocked = vehicleLocked,
        autoMode = robotMode == RobotMode.AUTO,
    )

private fun scaleSignedControl(value: Float): Int =
    (value.coerceIn(-1f, 1f) * ProtocolConstants.MAX_SIGNED_CONTROL_VALUE.toFloat()).roundToInt()

private fun scaleUnsignedToSigned(percent: Int): Int =
    (percent.coerceIn(0, 100) / 100f * ProtocolConstants.MAX_SIGNED_CONTROL_VALUE.toFloat()).roundToInt()
