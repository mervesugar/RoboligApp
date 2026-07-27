package com.robolig.controller.protocol

import com.robolig.controller.core.ProtocolConstants
import javax.inject.Inject
import javax.inject.Singleton

interface PacketParser {
    fun parse(rawPacket: ByteArray): PacketParseResult
}

@Singleton
class BinaryPacketParser
    @Inject
    constructor(
        private val checksum: Checksum,
    ) : PacketParser {
        override fun parse(rawPacket: ByteArray): PacketParseResult {
            val packetType = PacketType.fromWireValue(rawPacket.getOrNull(1)?.unsignedValue() ?: -1)
            val payload =
                if (rawPacket.size >= 28) {
                    rawPacket.copyOfRange(4, 28)
                } else {
                    ByteArray(ProtocolConstants.PAYLOAD_SIZE_BYTES)
                }
            val failure =
                validationFailure(
                    rawPacket = rawPacket,
                    packetType = packetType,
                    payload = payload,
                )

            if (failure != null || packetType == null) {
                return PacketParseResult.Failure(failure ?: PacketValidationFailure.UNKNOWN_PACKET_TYPE)
            }

            return PacketParseResult.Success(
                Packet(
                    type = packetType,
                    sequenceNumber = rawPacket[2].unsignedValue(),
                    flags = PacketFlags.fromWireValue(rawPacket[3].unsignedValue()),
                    payload = payload,
                    timestampMillis = readTimestamp(rawPacket),
                ),
            )
        }

        private fun validationFailure(
            rawPacket: ByteArray,
            packetType: PacketType?,
            payload: ByteArray,
        ): PacketValidationFailure? {
            val checksumFailure =
                if (rawPacket.size == ProtocolConstants.PACKET_SIZE_BYTES) {
                    val expectedChecksum =
                        checksum.compute(rawPacket.copyOf(ProtocolConstants.PACKET_SIZE_BYTES - 1))
                    rawPacket.last().unsignedValue() != expectedChecksum
                } else {
                    false
                }

            return when {
                rawPacket.size != ProtocolConstants.PACKET_SIZE_BYTES -> PacketValidationFailure.INVALID_LENGTH
                rawPacket[0].unsignedValue() != ProtocolConstants.HEADER -> PacketValidationFailure.INVALID_HEADER
                checksumFailure -> PacketValidationFailure.INVALID_CHECKSUM
                packetType == null -> PacketValidationFailure.UNKNOWN_PACKET_TYPE
                !isPayloadValid(packetType, payload) -> PacketValidationFailure.INVALID_PAYLOAD
                else -> null
            }
        }
    }

private fun isPayloadValid(
    packetType: PacketType,
    payload: ByteArray,
): Boolean =
    try {
        when (packetType) {
            PacketType.VEHICLE_CONTROL -> {
                VehicleControlPayload.fromPayload(payload)
            }
            PacketType.ARM_CONTROL -> {
                ArmControlPayload.fromPayload(payload)
            }
            PacketType.PTZ_CONTROL,
            PacketType.TELEMETRY_REQUEST,
            PacketType.EMERGENCY_STOP,
            PacketType.HEARTBEAT,
            PacketType.RFID_CONFIG_BEGIN,
            PacketType.RFID_CONFIG_ITEM,
            PacketType.RFID_CONFIG_COMMIT,
            PacketType.RFID_CONFIG_ACK,
            PacketType.RFID_CONFIG_NACK,
            -> Unit
            PacketType.TELEMETRY_RESPONSE -> {
                TelemetryResponsePayload.fromPayload(payload)
            }
        }
        true
    } catch (_: IllegalArgumentException) {
        false
    }
