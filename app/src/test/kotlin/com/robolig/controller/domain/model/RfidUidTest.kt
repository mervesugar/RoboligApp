package com.robolig.controller.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RfidUidTest {
    @Test
    fun `AABBCCDD is accepted`() {
        val result = RfidUid.create("AABBCCDD")
        assertTrue(result.isSuccess)
        assertEquals("AABBCCDD", result.getOrNull()?.displayUid)
        assertEquals(4, result.getOrNull()?.uidLength)
    }

    @Test
    fun `AA BB CC DD is normalized`() {
        val result = RfidUid.create("AA BB CC DD")
        assertTrue(result.isSuccess)
        assertEquals("AABBCCDD", result.getOrNull()?.displayUid)
    }

    @Test
    fun `AA colon BB colon CC colon DD is normalized`() {
        val result = RfidUid.create("AA:BB:CC:DD")
        assertTrue(result.isSuccess)
        assertEquals("AABBCCDD", result.getOrNull()?.displayUid)
    }

    @Test
    fun `AA-BB-CC-DD is normalized`() {
        val result = RfidUid.create("AA-BB-CC-DD")
        assertTrue(result.isSuccess)
        assertEquals("AABBCCDD", result.getOrNull()?.displayUid)
    }

    @Test
    fun `lowercase is uppercased`() {
        val result = RfidUid.create("aabbccdd")
        assertTrue(result.isSuccess)
        assertEquals("AABBCCDD", result.getOrNull()?.displayUid)
    }

    @Test
    fun `4 byte UID is accepted`() {
        val result = RfidUid.create("1A2B3C4D")
        assertTrue(result.isSuccess)
        assertEquals(4, result.getOrNull()?.uidLength)
    }

    @Test
    fun `7 byte UID is accepted`() {
        val result = RfidUid.create("01020304050607")
        assertTrue(result.isSuccess)
        assertEquals(7, result.getOrNull()?.uidLength)
    }

    @Test
    fun `10 byte UID is accepted`() {
        val result = RfidUid.create("0102030405060708090A")
        assertTrue(result.isSuccess)
        assertEquals(10, result.getOrNull()?.uidLength)
    }

    @Test
    fun `empty UID is rejected`() {
        val result = RfidUid.create("")
        assertFalse(result.isSuccess)
    }

    @Test
    fun `invalid hex is rejected`() {
        val result = RfidUid.create("1A2B3C4G") // G is not hex
        assertFalse(result.isSuccess)
    }

    @Test
    fun `odd number of hex is rejected`() {
        val result = RfidUid.create("1A2B3C4") // 7 chars
        assertFalse(result.isSuccess)
    }

    @Test
    fun `3 byte UID is rejected`() {
        val result = RfidUid.create("1A2B3C") // 3 bytes
        assertFalse(result.isSuccess)
    }

    @Test
    fun `5 byte UID is rejected`() {
        val result = RfidUid.create("1A2B3C4D5E") // 5 bytes
        assertFalse(result.isSuccess)
    }

    @Test
    fun `models with same raw UID content are equal`() {
        val uid1 = RfidUid.create("AA BB CC DD").getOrNull()
        val uid2 = RfidUid.create("AA:BB:CC:DD").getOrNull()
        val uid3 = RfidUid.create("aa-bb-cc-dd").getOrNull()
        val uid4 = RfidUid.create("AABBCCDD").getOrNull()

        assertEquals(uid1, uid2)
        assertEquals(uid1, uid3)
        assertEquals(uid1, uid4)
    }

    @Test
    fun `models with different raw UID content are not equal`() {
        val uid1 = RfidUid.create("AABBCCDD").getOrNull()
        val uid2 = RfidUid.create("AABBCCEE").getOrNull()

        assertNotEquals(uid1, uid2)
    }

    @Test
    fun `hashCode is based on content`() {
        val uid1 = RfidUid.create("AA BB CC DD").getOrNull()
        val uid2 = RfidUid.create("AABBCCDD").getOrNull()

        assertEquals(uid1?.hashCode(), uid2?.hashCode())
    }
}
