package com.robolig.controller.protocol

import com.robolig.controller.core.ProtocolConstants
import com.robolig.controller.domain.model.City
import com.robolig.controller.domain.model.RfidUid
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RfidPayloadsTest {
    @Test
    fun `RfidConfigBeginPayload encoding and decoding`() {
        val payload =
            RfidConfigBeginPayload(
                protocolVersion = 1,
                sessionId = 42,
                tableVersion = 1,
                expectedRecordCount = 5,
                tableChecksum = 0x12345678L,
            )
        val bytes = payload.toPayloadBytes()
        assertEquals(ProtocolConstants.PAYLOAD_SIZE_BYTES, bytes.size)

        val decoded = RfidConfigBeginPayload.fromPayload(bytes)
        assertEquals(1, decoded.protocolVersion)
        assertEquals(42, decoded.sessionId)
        assertEquals(1, decoded.tableVersion)
        assertEquals(5, decoded.expectedRecordCount)
        assertEquals(0x12345678L, decoded.tableChecksum)
    }

    @Test
    fun `RfidConfigItemPayload encoding and decoding for 4 byte UID`() {
        val uid = RfidUid.create("AABBCCDD").getOrNull()!!
        val payload =
            RfidConfigItemPayload(
                sessionId = 10,
                tableVersion = 1,
                recordIndex = 0,
                cityCode = City.SINOP.code,
                uidLength = uid.uidLength,
                rawUidBytes = uid.rawUidBytes,
                enabled = true,
            )
        val bytes = payload.toPayloadBytes()
        assertEquals(ProtocolConstants.PAYLOAD_SIZE_BYTES, bytes.size)

        for (i in 9..14) {
            assertEquals(0.toByte(), bytes[i])
        }

        val decoded = RfidConfigItemPayload.fromPayload(bytes)
        assertEquals(10, decoded.sessionId)
        assertEquals(1, decoded.tableVersion)
        assertEquals(0, decoded.recordIndex)
        assertEquals(1, decoded.cityCode)
        assertEquals(4, decoded.uidLength)
        assertArrayEquals(uid.rawUidBytes, decoded.rawUidBytes)
        assertTrue(decoded.enabled)
    }

    @Test
    fun `RfidConfigItemPayload encoding and decoding for 7 byte UID`() {
        val uid = RfidUid.create("01020304050607").getOrNull()!!
        val payload =
            RfidConfigItemPayload(
                sessionId = 15,
                tableVersion = 1,
                recordIndex = 1,
                cityCode = City.NIGDE.code,
                uidLength = uid.uidLength,
                rawUidBytes = uid.rawUidBytes,
                enabled = true,
            )
        val bytes = payload.toPayloadBytes()
        val decoded = RfidConfigItemPayload.fromPayload(bytes)
        assertEquals(7, decoded.uidLength)
        assertArrayEquals(uid.rawUidBytes, decoded.rawUidBytes)
    }

    @Test
    fun `RfidConfigItemPayload encoding and decoding for 10 byte UID`() {
        val uid = RfidUid.create("0102030405060708090A").getOrNull()!!
        val payload =
            RfidConfigItemPayload(
                sessionId = 20,
                tableVersion = 1,
                recordIndex = 2,
                cityCode = City.TOKAT.code,
                uidLength = uid.uidLength,
                rawUidBytes = uid.rawUidBytes,
                enabled = true,
            )
        val bytes = payload.toPayloadBytes()
        val decoded = RfidConfigItemPayload.fromPayload(bytes)
        assertEquals(10, decoded.uidLength)
        assertArrayEquals(uid.rawUidBytes, decoded.rawUidBytes)
    }

    @Test
    fun `RfidConfigCommitPayload encoding and decoding`() {
        val payload =
            RfidConfigCommitPayload(
                sessionId = 99,
                tableVersion = 1,
                recordCount = 5,
                tableChecksum = 0x87654321L,
            )
        val bytes = payload.toPayloadBytes()
        assertEquals(ProtocolConstants.PAYLOAD_SIZE_BYTES, bytes.size)

        val decoded = RfidConfigCommitPayload.fromPayload(bytes)
        assertEquals(99, decoded.sessionId)
        assertEquals(1, decoded.tableVersion)
        assertEquals(5, decoded.recordCount)
        assertEquals(0x87654321L, decoded.tableChecksum)
    }

    @Test
    fun `RfidConfigAckPayload encoding and decoding`() {
        val payload =
            RfidConfigAckPayload(
                sessionId = 33,
                acknowledgedPacketType = PacketType.RFID_CONFIG_ITEM.wireValue,
                recordIndex = 3,
                statusCode = 0,
                recordCount = 5,
                activeTableVersion = 1,
                activeTableChecksum = 0xABCDEF12L,
            )
        val bytes = payload.toPayloadBytes()
        val decoded = RfidConfigAckPayload.fromPayload(bytes)

        assertEquals(33, decoded.sessionId)
        assertEquals(PacketType.RFID_CONFIG_ITEM.wireValue, decoded.acknowledgedPacketType)
        assertEquals(3, decoded.recordIndex)
        assertEquals(0, decoded.statusCode)
        assertEquals(5, decoded.recordCount)
        assertEquals(0xABCDEF12L, decoded.activeTableChecksum)
    }

    @Test
    fun `RfidConfigNackPayload encoding and decoding`() {
        val payload =
            RfidConfigNackPayload(
                sessionId = 55,
                acknowledgedPacketType = PacketType.RFID_CONFIG_BEGIN.wireValue,
                recordIndex = 0xFF,
                errorCode = RfidNackErrorCode.DUPLICATE_UID.code,
            )
        val bytes = payload.toPayloadBytes()
        val decoded = RfidConfigNackPayload.fromPayload(bytes)

        assertEquals(55, decoded.sessionId)
        assertEquals(RfidNackErrorCode.DUPLICATE_UID, decoded.error)
    }

    @Test
    fun `RfidTableChecksum is deterministic for identical 5 city entries`() {
        val cityUids1 =
            mapOf(
                City.SINOP to RfidUid.create("11223344").getOrNull()!!,
                City.NIGDE to RfidUid.create("22334455").getOrNull()!!,
                City.TOKAT to RfidUid.create("33445566").getOrNull()!!,
                City.AYDIN to RfidUid.create("44556677").getOrNull()!!,
                City.ELAZIG to RfidUid.create("55667788").getOrNull()!!,
            )

        val cityUids2 =
            mapOf(
                City.SINOP to RfidUid.create("11 22 33 44").getOrNull()!!,
                City.NIGDE to RfidUid.create("22:33:44:55").getOrNull()!!,
                City.TOKAT to RfidUid.create("33-44-55-66").getOrNull()!!,
                City.AYDIN to RfidUid.create("44556677").getOrNull()!!,
                City.ELAZIG to RfidUid.create("55667788").getOrNull()!!,
            )

        val checksum1 = RfidTableChecksum.calculate(cityUids1)
        val checksum2 = RfidTableChecksum.calculate(cityUids2)

        assertEquals(checksum1, checksum2)
    }
}
