package com.robolig.controller.domain.rfid

import com.robolig.controller.communication.CommandQueue
import com.robolig.controller.communication.CommunicationStateStore
import com.robolig.controller.core.AppLogger
import com.robolig.controller.core.LogTag
import com.robolig.controller.domain.model.City
import com.robolig.controller.domain.model.RfidUid
import com.robolig.controller.protocol.Packet
import com.robolig.controller.protocol.PacketSequenceGenerator
import com.robolig.controller.protocol.PacketType
import com.robolig.controller.protocol.RfidConfigAckPayload
import com.robolig.controller.protocol.RfidConfigBeginPayload
import com.robolig.controller.protocol.RfidConfigItemPayload
import com.robolig.controller.protocol.RfidConfigNackPayload
import com.robolig.controller.protocol.RfidNackErrorCode
import com.robolig.controller.protocol.RobotPacketFactory
import com.robolig.controller.utils.MonotonicClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeLogger : AppLogger {
    override fun d(
        tag: LogTag,
        message: String,
    ) = Unit

    override fun i(
        tag: LogTag,
        message: String,
    ) = Unit

    override fun w(
        tag: LogTag,
        message: String,
    ) = Unit

    override fun e(
        tag: LogTag,
        message: String,
        throwable: Throwable?,
    ) = Unit
}

class FakeMonotonicClock : MonotonicClock {
    private var time = 1000L

    override fun elapsedRealtimeMs(): Long = time

    fun advance(ms: Long) {
        time += ms
    }
}

class RfidSyncManagerTest {
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    private lateinit var clock: FakeMonotonicClock
    private lateinit var commandQueue: CommandQueue
    private lateinit var packetFactory: RobotPacketFactory
    private lateinit var stateStore: CommunicationStateStore
    private lateinit var syncManager: RfidSyncManager

    private val sampleCityUids =
        mapOf(
            City.SINOP to RfidUid.create("11223344").getOrNull()!!,
            City.NIGDE to RfidUid.create("22334455").getOrNull()!!,
            City.TOKAT to RfidUid.create("33445566").getOrNull()!!,
            City.AYDIN to RfidUid.create("44556677").getOrNull()!!,
            City.ELAZIG to RfidUid.create("55667788").getOrNull()!!,
        )

    @Before
    fun setup() {
        clock = FakeMonotonicClock()
        commandQueue = CommandQueue(clock)
        packetFactory = RobotPacketFactory(clock, PacketSequenceGenerator())
        stateStore = CommunicationStateStore()
        syncManager =
            RfidSyncManager(
                commandQueue = commandQueue,
                packetFactory = packetFactory,
                stateStore = stateStore,
                logger = FakeLogger(),
                applicationScope = testScope,
            )
    }

    @Test
    fun `successful sync flow BEGIN to 5 ITEMs to COMMIT to Success`() =
        runBlocking {
            val started = syncManager.startSync(sampleCityUids)
            assertTrue(started)

            val beginQueued = commandQueue.poll()!!
            assertEquals(PacketType.RFID_CONFIG_BEGIN, beginQueued.packetType)
            val beginPayload = RfidConfigBeginPayload.fromPayload(beginQueued.packet.payload)
            val sessionId = beginPayload.sessionId

            syncManager.onRfidPacketReceived(
                Packet(
                    type = PacketType.RFID_CONFIG_ACK,
                    sequenceNumber = 1,
                    payload =
                        RfidConfigAckPayload(
                            sessionId = sessionId,
                            acknowledgedPacketType = PacketType.RFID_CONFIG_BEGIN.wireValue,
                            recordIndex = 0xFF,
                        ).toPayloadBytes(),
                ),
            )

            for (i in 0..4) {
                val itemQueued = commandQueue.poll()!!
                assertEquals(PacketType.RFID_CONFIG_ITEM, itemQueued.packetType)
                val itemPayload = RfidConfigItemPayload.fromPayload(itemQueued.packet.payload)
                assertEquals(i, itemPayload.recordIndex)

                syncManager.onRfidPacketReceived(
                    Packet(
                        type = PacketType.RFID_CONFIG_ACK,
                        sequenceNumber = i + 2,
                        payload =
                            RfidConfigAckPayload(
                                sessionId = sessionId,
                                acknowledgedPacketType = PacketType.RFID_CONFIG_ITEM.wireValue,
                                recordIndex = i,
                            ).toPayloadBytes(),
                    ),
                )
            }

            val stateBeforeCommitAck = syncManager.syncState.value
            assertFalse(stateBeforeCommitAck is RfidSyncState.Success)

            val commitQueued = commandQueue.poll()!!
            assertEquals(PacketType.RFID_CONFIG_COMMIT, commitQueued.packetType)

            syncManager.onRfidPacketReceived(
                Packet(
                    type = PacketType.RFID_CONFIG_ACK,
                    sequenceNumber = 10,
                    payload =
                        RfidConfigAckPayload(
                            sessionId = sessionId,
                            acknowledgedPacketType = PacketType.RFID_CONFIG_COMMIT.wireValue,
                            recordIndex = 0xFF,
                        ).toPayloadBytes(),
                ),
            )

            assertEquals(RfidSyncState.Success, syncManager.syncState.value)
        }

    @Test
    fun `NACK response fails sync with error code`() =
        runBlocking {
            syncManager.startSync(sampleCityUids)
            val beginQueued = commandQueue.poll()!!
            val sessionId = RfidConfigBeginPayload.fromPayload(beginQueued.packet.payload).sessionId

            syncManager.onRfidPacketReceived(
                Packet(
                    type = PacketType.RFID_CONFIG_NACK,
                    sequenceNumber = 1,
                    payload =
                        RfidConfigNackPayload(
                            sessionId = sessionId,
                            acknowledgedPacketType = PacketType.RFID_CONFIG_BEGIN.wireValue,
                            recordIndex = 0xFF,
                            errorCode = RfidNackErrorCode.DUPLICATE_UID.code,
                        ).toPayloadBytes(),
                ),
            )

            val finalState = syncManager.syncState.value
            assertTrue(finalState is RfidSyncState.Failed)
            val failedState = finalState as RfidSyncState.Failed
            assertEquals(RfidNackErrorCode.DUPLICATE_UID.code, failedState.errorCode)
            assertTrue(failedState.errorMessage.contains("Duplicate UID"))
        }

    @Test
    fun `wrong session ACK is ignored`() =
        runBlocking {
            syncManager.startSync(sampleCityUids)
            val beginQueued = commandQueue.poll()!!
            val sessionId = RfidConfigBeginPayload.fromPayload(beginQueued.packet.payload).sessionId

            syncManager.onRfidPacketReceived(
                Packet(
                    type = PacketType.RFID_CONFIG_ACK,
                    sequenceNumber = 1,
                    payload =
                        RfidConfigAckPayload(
                            sessionId = sessionId + 10,
                            acknowledgedPacketType = PacketType.RFID_CONFIG_BEGIN.wireValue,
                            recordIndex = 0xFF,
                        ).toPayloadBytes(),
                ),
            )

            assertFalse(syncManager.syncState.value is RfidSyncState.Success)
        }
}
