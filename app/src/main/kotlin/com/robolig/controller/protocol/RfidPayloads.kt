package com.robolig.controller.protocol

import com.robolig.controller.core.ProtocolConstants
import com.robolig.controller.domain.model.City
import com.robolig.controller.domain.model.RfidUid
import java.util.zip.CRC32

enum class RfidNackErrorCode(val code: Int, val description: String) {
    NONE(0, "No Error"),
    INVALID_UID(1, "Invalid UID"),
    INVALID_UID_LENGTH(2, "Invalid UID Length"),
    DUPLICATE_UID(3, "Duplicate UID"),
    INVALID_CITY(4, "Invalid City"),
    RECORD_COUNT_MISMATCH(5, "Record Count Mismatch"),
    SESSION_MISMATCH(6, "Session Mismatch"),
    TABLE_CHECKSUM_MISMATCH(7, "Table Checksum Mismatch"),
    STORAGE_ERROR(8, "Storage Error"),
    BUSY(9, "Robot Busy"),
    UNSUPPORTED_VERSION(10, "Unsupported Version"),
    TIMEOUT(11, "Robot Timeout"),
    ;

    companion object {
        fun fromCode(code: Int): RfidNackErrorCode =
            entries.firstOrNull { it.code == code } ?: TIMEOUT
    }
}

object RfidTableChecksum {
    fun calculate(cityUids: Map<City, RfidUid>): Long {
        require(cityUids.size == 5) { "Table checksum requires exactly 5 city entries" }
        val crc = CRC32()
        val orderedCities = listOf(City.SINOP, City.NIGDE, City.TOKAT, City.AYDIN, City.ELAZIG)
        for ((index, city) in orderedCities.withIndex()) {
            val uid = cityUids[city] ?: throw IllegalArgumentException("Missing UID for city ${city.name}")
            crc.update(index)
            crc.update(city.code)
            crc.update(uid.uidLength)
            val paddedBytes = ByteArray(10)
            uid.rawUidBytes.copyInto(paddedBytes)
            crc.update(paddedBytes)
            crc.update(1) // Enabled flag
        }
        return crc.value
    }

    fun checksumToBytes(checksum: Long): ByteArray =
        byteArrayOf(
            ((checksum shr 24) and 0xFF).toByte(),
            ((checksum shr 16) and 0xFF).toByte(),
            ((checksum shr 8) and 0xFF).toByte(),
            (checksum and 0xFF).toByte(),
        )

    fun bytesToChecksum(
        payload: ByteArray,
        offset: Int,
    ): Long =
        ((payload[offset].toLong() and 0xFF) shl 24) or
            ((payload[offset + 1].toLong() and 0xFF) shl 16) or
            ((payload[offset + 2].toLong() and 0xFF) shl 8) or
            (payload[offset + 3].toLong() and 0xFF)
}

data class RfidConfigBeginPayload(
    val protocolVersion: Int = 1,
    val sessionId: Int,
    val tableVersion: Int = 1,
    val expectedRecordCount: Int = 5,
    val tableChecksum: Long,
) {
    fun toPayloadBytes(): ByteArray =
        ByteArray(ProtocolConstants.PAYLOAD_SIZE_BYTES).apply {
            this[0] = protocolVersion.toByte()
            this[1] = sessionId.toByte()
            this[2] = tableVersion.toByte()
            this[3] = expectedRecordCount.toByte()
            val checksumBytes = RfidTableChecksum.checksumToBytes(tableChecksum)
            checksumBytes.copyInto(this, destinationOffset = 4)
        }

    companion object {
        fun fromPayload(payload: ByteArray): RfidConfigBeginPayload =
            RfidConfigBeginPayload(
                protocolVersion = payload[0].unsignedValue(),
                sessionId = payload[1].unsignedValue(),
                tableVersion = payload[2].unsignedValue(),
                expectedRecordCount = payload[3].unsignedValue(),
                tableChecksum = RfidTableChecksum.bytesToChecksum(payload, 4),
            )
    }
}

data class RfidConfigItemPayload(
    val sessionId: Int,
    val tableVersion: Int = 1,
    val recordIndex: Int,
    val cityCode: Int,
    val uidLength: Int,
    val rawUidBytes: ByteArray,
    val enabled: Boolean = true,
) {
    init {
        require(recordIndex in 0..4) { "Record index must be 0 to 4" }
        require(cityCode in 1..5) { "City code must be 1 to 5" }
        require(uidLength in setOf(4, 7, 10)) { "UID length must be 4, 7, or 10 bytes" }
    }

    fun toPayloadBytes(): ByteArray =
        ByteArray(ProtocolConstants.PAYLOAD_SIZE_BYTES).apply {
            this[0] = sessionId.toByte()
            this[1] = tableVersion.toByte()
            this[2] = recordIndex.toByte()
            this[3] = cityCode.toByte()
            this[4] = uidLength.toByte()
            val paddedUid = ByteArray(10)
            val endIdx = minOf(rawUidBytes.size, 10)
            rawUidBytes.copyInto(paddedUid, destinationOffset = 0, startIndex = 0, endIndex = endIdx)
            paddedUid.copyInto(this, destinationOffset = 5)
            this[15] = if (enabled) 1 else 0
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RfidConfigItemPayload) return false
        return sessionId == other.sessionId &&
            tableVersion == other.tableVersion &&
            recordIndex == other.recordIndex &&
            cityCode == other.cityCode &&
            uidLength == other.uidLength &&
            rawUidBytes.contentEquals(other.rawUidBytes) &&
            enabled == other.enabled
    }

    override fun hashCode(): Int {
        var result = sessionId
        result = 31 * result + tableVersion
        result = 31 * result + recordIndex
        result = 31 * result + cityCode
        result = 31 * result + uidLength
        result = 31 * result + rawUidBytes.contentHashCode()
        result = 31 * result + enabled.hashCode()
        return result
    }

    companion object {
        fun fromPayload(payload: ByteArray): RfidConfigItemPayload {
            val length = payload[4].unsignedValue()
            val rawUid = payload.copyOfRange(5, 5 + minOf(length, 10))
            return RfidConfigItemPayload(
                sessionId = payload[0].unsignedValue(),
                tableVersion = payload[1].unsignedValue(),
                recordIndex = payload[2].unsignedValue(),
                cityCode = payload[3].unsignedValue(),
                uidLength = length,
                rawUidBytes = rawUid,
                enabled = payload[15].unsignedValue() != 0,
            )
        }
    }
}

data class RfidConfigCommitPayload(
    val sessionId: Int,
    val tableVersion: Int = 1,
    val recordCount: Int = 5,
    val tableChecksum: Long,
) {
    fun toPayloadBytes(): ByteArray =
        ByteArray(ProtocolConstants.PAYLOAD_SIZE_BYTES).apply {
            this[0] = sessionId.toByte()
            this[1] = tableVersion.toByte()
            this[2] = recordCount.toByte()
            val checksumBytes = RfidTableChecksum.checksumToBytes(tableChecksum)
            checksumBytes.copyInto(this, destinationOffset = 3)
        }

    companion object {
        fun fromPayload(payload: ByteArray): RfidConfigCommitPayload =
            RfidConfigCommitPayload(
                sessionId = payload[0].unsignedValue(),
                tableVersion = payload[1].unsignedValue(),
                recordCount = payload[2].unsignedValue(),
                tableChecksum = RfidTableChecksum.bytesToChecksum(payload, 3),
            )
    }
}

data class RfidConfigAckPayload(
    val sessionId: Int,
    val acknowledgedPacketType: Int,
    val recordIndex: Int,
    val statusCode: Int = 0,
    val recordCount: Int = 5,
    val activeTableVersion: Int = 1,
    val activeTableChecksum: Long = 0L,
) {
    fun toPayloadBytes(): ByteArray =
        ByteArray(ProtocolConstants.PAYLOAD_SIZE_BYTES).apply {
            this[0] = sessionId.toByte()
            this[1] = acknowledgedPacketType.toByte()
            this[2] = recordIndex.toByte()
            this[3] = statusCode.toByte()
            this[4] = recordCount.toByte()
            this[5] = activeTableVersion.toByte()
            val checksumBytes = RfidTableChecksum.checksumToBytes(activeTableChecksum)
            checksumBytes.copyInto(this, destinationOffset = 6)
        }

    companion object {
        fun fromPayload(payload: ByteArray): RfidConfigAckPayload =
            RfidConfigAckPayload(
                sessionId = payload[0].unsignedValue(),
                acknowledgedPacketType = payload[1].unsignedValue(),
                recordIndex = payload[2].unsignedValue(),
                statusCode = payload[3].unsignedValue(),
                recordCount = payload[4].unsignedValue(),
                activeTableVersion = payload[5].unsignedValue(),
                activeTableChecksum = RfidTableChecksum.bytesToChecksum(payload, 6),
            )
    }
}

data class RfidConfigNackPayload(
    val sessionId: Int,
    val acknowledgedPacketType: Int,
    val recordIndex: Int,
    val errorCode: Int,
    val recordCount: Int = 0,
    val activeTableVersion: Int = 0,
    val activeTableChecksum: Long = 0L,
) {
    val error: RfidNackErrorCode
        get() = RfidNackErrorCode.fromCode(errorCode)

    fun toPayloadBytes(): ByteArray =
        ByteArray(ProtocolConstants.PAYLOAD_SIZE_BYTES).apply {
            this[0] = sessionId.toByte()
            this[1] = acknowledgedPacketType.toByte()
            this[2] = recordIndex.toByte()
            this[3] = errorCode.toByte()
            this[4] = recordCount.toByte()
            this[5] = activeTableVersion.toByte()
            val checksumBytes = RfidTableChecksum.checksumToBytes(activeTableChecksum)
            checksumBytes.copyInto(this, destinationOffset = 6)
        }

    companion object {
        fun fromPayload(payload: ByteArray): RfidConfigNackPayload =
            RfidConfigNackPayload(
                sessionId = payload[0].unsignedValue(),
                acknowledgedPacketType = payload[1].unsignedValue(),
                recordIndex = payload[2].unsignedValue(),
                errorCode = payload[3].unsignedValue(),
                recordCount = payload[4].unsignedValue(),
                activeTableVersion = payload[5].unsignedValue(),
                activeTableChecksum = RfidTableChecksum.bytesToChecksum(payload, 6),
            )
    }
}
