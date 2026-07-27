package com.robolig.controller.protocol

import com.robolig.controller.core.ProtocolConstants
import com.robolig.controller.domain.model.RobotMode

enum class PacketType(val wireValue: Int) {
    VEHICLE_CONTROL(0x01),
    ARM_CONTROL(0x02),
    PTZ_CONTROL(0x03),
    TELEMETRY_REQUEST(0x04),
    TELEMETRY_RESPONSE(0x05),
    EMERGENCY_STOP(0x0E),
    HEARTBEAT(0x0F),
    RFID_CONFIG_BEGIN(0x20),
    RFID_CONFIG_ITEM(0x21),
    RFID_CONFIG_COMMIT(0x22),
    RFID_CONFIG_ACK(0x23),
    RFID_CONFIG_NACK(0x24),
    ;

    companion object {
        fun fromWireValue(value: Int): PacketType? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class PacketPriority(val order: Int) {
    EMERGENCY_STOP(0),
    HEARTBEAT(1),
    STANDARD(2),
    TELEMETRY(3),
}

data class PacketFlags(
    val emergencyStop: Boolean = false,
    val precisionMode: Boolean = false,
    val armLocked: Boolean = false,
    val vehicleLocked: Boolean = false,
    val autoMode: Boolean = false,
) {
    fun toWireValue(): Int {
        var value = 0
        if (emergencyStop) {
            value = value or 0b0000_0001
        }
        if (precisionMode) {
            value = value or 0b0000_0010
        }
        if (armLocked) {
            value = value or 0b0000_0100
        }
        if (vehicleLocked) {
            value = value or 0b0000_1000
        }
        if (autoMode) {
            value = value or 0b0001_0000
        }
        return value
    }

    companion object {
        fun fromWireValue(value: Int): PacketFlags =
            PacketFlags(
                emergencyStop = value and 0b0000_0001 != 0,
                precisionMode = value and 0b0000_0010 != 0,
                armLocked = value and 0b0000_0100 != 0,
                vehicleLocked = value and 0b0000_1000 != 0,
                autoMode = value and 0b0001_0000 != 0,
            )
    }
}

class Packet(
    val type: PacketType,
    val sequenceNumber: Int,
    val flags: PacketFlags = PacketFlags(),
    val payload: ByteArray = ByteArray(ProtocolConstants.PAYLOAD_SIZE_BYTES),
    val timestampMillis: Int = 0,
) {
    init {
        require(sequenceNumber in 0..0xFF) { "Sequence number must fit in one byte" }
        require(payload.size == ProtocolConstants.PAYLOAD_SIZE_BYTES) { "Payload must be exactly 24 bytes" }
        require(timestampMillis in 0..ProtocolConstants.TIMESTAMP_MASK) { "Timestamp must fit in 24 bits" }
    }

    override fun equals(other: Any?): Boolean =
        other is Packet &&
            type == other.type &&
            sequenceNumber == other.sequenceNumber &&
            flags == other.flags &&
            payload.contentEquals(other.payload) &&
            timestampMillis == other.timestampMillis

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + sequenceNumber
        result = 31 * result + flags.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + timestampMillis
        return result
    }
}

enum class PacketValidationFailure {
    INVALID_HEADER,
    INVALID_LENGTH,
    INVALID_CHECKSUM,
    UNKNOWN_PACKET_TYPE,
    INVALID_SEQUENCE,
    INVALID_PAYLOAD,
}

sealed interface PacketParseResult {
    data class Success(val packet: Packet) : PacketParseResult

    data class Failure(val reason: PacketValidationFailure) : PacketParseResult
}

sealed interface PacketDecodeResult {
    data class Success(
        val packet: Packet,
        val droppedSequenceCount: Int,
    ) : PacketDecodeResult

    data class Failure(val reason: PacketValidationFailure) : PacketDecodeResult
}

data class VehicleControlPayload(
    val moveX: Int,
    val moveY: Int,
    val rotation: Int,
    val throttle: Int,
    val brake: Int,
    val boost: Int,
) {
    init {
        validateSignedByte(moveX)
        validateSignedByte(moveY)
        validateSignedByte(rotation)
        validateSignedByte(throttle)
        validateSignedByte(brake)
        validateSignedByte(boost)
    }

    fun toPayloadBytes(): ByteArray =
        ByteArray(ProtocolConstants.PAYLOAD_SIZE_BYTES).apply {
            this[0] = moveX.toByte()
            this[1] = moveY.toByte()
            this[2] = rotation.toByte()
            this[3] = throttle.toByte()
            this[4] = brake.toByte()
            this[5] = boost.toByte()
        }

    companion object {
        fun fromPayload(payload: ByteArray): VehicleControlPayload =
            VehicleControlPayload(
                moveX = payload[0].toInt(),
                moveY = payload[1].toInt(),
                rotation = payload[2].toInt(),
                throttle = payload[3].toInt(),
                brake = payload[4].toInt(),
                boost = payload[5].toInt(),
            )
    }
}

data class ArmControlPayload(
    val shoulder: Int,
    val elbow: Int,
    val wristPitch: Int,
    val wristRoll: Int,
    val gripperRotation: Int,
    val gripper: Int,
) {
    init {
        validateServoAngle(shoulder)
        validateServoAngle(elbow)
        validateServoAngle(wristPitch)
        validateServoAngle(wristRoll)
        validateServoAngle(gripperRotation)
        require(gripper in ProtocolConstants.GRIPPER_CLOSED..ProtocolConstants.GRIPPER_OPEN) {
            "Gripper command must be within the protocol range"
        }
    }

    fun toPayloadBytes(): ByteArray =
        ByteArray(ProtocolConstants.PAYLOAD_SIZE_BYTES).apply {
            this[0] = shoulder.toByte()
            this[1] = elbow.toByte()
            this[2] = wristPitch.toByte()
            this[3] = wristRoll.toByte()
            this[4] = gripperRotation.toByte()
            this[5] = gripper.toByte()
        }

    companion object {
        fun fromPayload(payload: ByteArray): ArmControlPayload =
            ArmControlPayload(
                shoulder = payload[0].unsignedValue(),
                elbow = payload[1].unsignedValue(),
                wristPitch = payload[2].unsignedValue(),
                wristRoll = payload[3].unsignedValue(),
                gripperRotation = payload[4].unsignedValue(),
                gripper = payload[5].unsignedValue(),
            )
    }
}

data class TelemetryResponsePayload(
    val batteryPercent: Int,
    val signalPercent: Int,
    val currentSpeedRaw: Int,
    val temperatureCelsius: Int,
    val currentMode: RobotMode,
    val errorCode: Int,
    val motorCurrentRaw: Int,
    val armCurrentRaw: Int,
    val cpuLoadPercent: Int,
) {
    companion object {
        fun fromPayload(payload: ByteArray): TelemetryResponsePayload {
            val robotMode =
                RobotMode.entries.getOrNull(payload[4].unsignedValue())
                    ?: throw IllegalArgumentException("Invalid robot mode in telemetry payload")

            return TelemetryResponsePayload(
                batteryPercent = payload[0].unsignedValue(),
                signalPercent = payload[1].unsignedValue(),
                currentSpeedRaw = payload[2].unsignedValue(),
                temperatureCelsius = payload[3].unsignedValue(),
                currentMode = robotMode,
                errorCode = payload[5].unsignedValue(),
                motorCurrentRaw = payload[6].unsignedValue(),
                armCurrentRaw = payload[7].unsignedValue(),
                cpuLoadPercent = payload[8].unsignedValue(),
            )
        }
    }
}

private fun validateSignedByte(value: Int) {
    require(value in ProtocolConstants.MIN_SIGNED_CONTROL_VALUE..ProtocolConstants.MAX_SIGNED_CONTROL_VALUE) {
        "Signed packet value must remain within protocol bounds"
    }
}

private fun validateServoAngle(value: Int) {
    require(value in ProtocolConstants.MIN_SERVO_ANGLE..ProtocolConstants.MAX_SERVO_ANGLE) {
        "Servo angle must remain within protocol bounds"
    }
}

internal fun Byte.unsignedValue(): Int = toInt().and(0xFF)
