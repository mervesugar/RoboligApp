package com.robolig.controller.domain.rfid

import com.robolig.controller.communication.CommandQueue
import com.robolig.controller.communication.CommunicationStateStore
import com.robolig.controller.core.AppLogger
import com.robolig.controller.core.ApplicationScope
import com.robolig.controller.core.LogTag
import com.robolig.controller.domain.model.City
import com.robolig.controller.domain.model.RfidUid
import com.robolig.controller.protocol.Packet
import com.robolig.controller.protocol.PacketPriority
import com.robolig.controller.protocol.PacketType
import com.robolig.controller.protocol.RfidConfigAckPayload
import com.robolig.controller.protocol.RfidConfigBeginPayload
import com.robolig.controller.protocol.RfidConfigCommitPayload
import com.robolig.controller.protocol.RfidConfigItemPayload
import com.robolig.controller.protocol.RfidConfigNackPayload
import com.robolig.controller.protocol.RfidNackErrorCode
import com.robolig.controller.protocol.RfidTableChecksum
import com.robolig.controller.protocol.RobotPacketFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

private data class StepRequest(
    val packet: Packet,
    val expectedPacketType: Int,
    val expectedRecordIndex: Int,
    val sessionId: Int,
    val stage: RfidSyncStage,
    val waitingState: RfidSyncState,
    val sendingState: RfidSyncState,
)

private sealed interface ResponseResult {
    data class Ack(val ackPayload: RfidConfigAckPayload) : ResponseResult

    data class Nack(val nackPayload: RfidConfigNackPayload) : ResponseResult

    data object Timeout : ResponseResult
}

@Singleton
class RfidSyncManager
    @Inject
    constructor(
        private val commandQueue: CommandQueue,
        private val packetFactory: RobotPacketFactory,
        private val stateStore: CommunicationStateStore,
        private val logger: AppLogger,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        companion object {
            const val TIMEOUT_MS = 1000L
            const val MAX_ATTEMPTS = 3
            const val RECORD_INDEX_ALL = 0xFF
        }

        private val _syncState = MutableStateFlow<RfidSyncState>(RfidSyncState.Idle)
        val syncState: StateFlow<RfidSyncState> = _syncState.asStateFlow()

        private val sessionIdGenerator = AtomicInteger(1)
        private val incomingRfidPackets = Channel<Packet>(Channel.UNLIMITED)
        private var currentSyncJob: Job? = null

        fun onRfidPacketReceived(packet: Packet) {
            if (packet.type == PacketType.RFID_CONFIG_ACK || packet.type == PacketType.RFID_CONFIG_NACK) {
                incomingRfidPackets.trySend(packet)
            }
        }

        fun startSync(cityUids: Map<City, RfidUid>): Boolean {
            val isBusy = isSyncActive(_syncState.value)
            if (!isBusy) {
                currentSyncJob?.cancel()
                currentSyncJob = applicationScope.launch { executeSync(cityUids) }
            } else {
                logger.w(LogTag.COMMUNICATION, "RFID sync already in progress")
            }
            return !isBusy
        }

        fun resetState() {
            currentSyncJob?.cancel()
            _syncState.value = RfidSyncState.Idle
        }

        private fun isSyncActive(state: RfidSyncState): Boolean =
            state is RfidSyncState.SendingBegin ||
                state is RfidSyncState.WaitingBeginAck ||
                state is RfidSyncState.SendingItem ||
                state is RfidSyncState.WaitingItemAck ||
                state is RfidSyncState.SendingCommit ||
                state is RfidSyncState.WaitingCommitAck ||
                state is RfidSyncState.Validating

        private suspend fun executeSync(cityUids: Map<City, RfidUid>) {
            _syncState.value = RfidSyncState.Validating
            val validationError = validateTableInput(cityUids)
            when {
                validationError != null -> {
                    _syncState.value = RfidSyncState.Failed(
                        errorMessage = validationError,
                        stage = RfidSyncStage.VALIDATING,
                    )
                }
                else -> {
                    val sessionId = generateSessionId()
                    val checksum = RfidTableChecksum.calculate(cityUids)
                    drainResidualPackets()
                    performSyncSequence(cityUids, sessionId, checksum)
                }
            }
        }

        private suspend fun performSyncSequence(
            cityUids: Map<City, RfidUid>,
            sessionId: Int,
            checksum: Long,
        ) {
            val beginOk = transmitBeginStep(sessionId, checksum)
            if (beginOk) {
                val itemsOk = transmitAllItemSteps(cityUids, sessionId)
                if (itemsOk) {
                    val commitOk = transmitCommitStep(sessionId, checksum)
                    if (commitOk) {
                        _syncState.value = RfidSyncState.Success
                        logger.i(LogTag.COMMUNICATION, "RFID sync completed. Checksum: $checksum")
                    }
                }
            }
        }

        private fun generateSessionId(): Int =
            (sessionIdGenerator.getAndIncrement() and 0xFF).let { if (it == 0) 1 else it }

        private fun drainResidualPackets() {
            while (incomingRfidPackets.tryReceive().isSuccess) {
                // Drain residual packets
            }
        }

        private fun validateTableInput(cityUids: Map<City, RfidUid>): String? {
            val orderedCities = listOf(City.SINOP, City.NIGDE, City.TOKAT, City.AYDIN, City.ELAZIG)
            val isComplete = cityUids.size == 5 && orderedCities.all { cityUids.containsKey(it) }
            val hasDupes = isComplete && hasDuplicates(orderedCities.map { cityUids.getValue(it) })
            return when {
                !isComplete -> "Eksik şehir kaydı."
                hasDupes -> "Tekrarlanan (Duplicate) UID tespit edildi."
                else -> null
            }
        }

        private fun hasDuplicates(uids: List<RfidUid>): Boolean {
            var found = false
            for (i in uids.indices) {
                for (j in i + 1 until uids.size) {
                    if (uids[i] == uids[j]) found = true
                }
            }
            return found
        }

        private suspend fun transmitBeginStep(sessionId: Int, checksum: Long): Boolean {
            _syncState.value = RfidSyncState.SendingBegin
            val payload = RfidConfigBeginPayload(
                sessionId = sessionId,
                tableVersion = 1,
                expectedRecordCount = 5,
                tableChecksum = checksum,
            )
            val packet = packetFactory.createRfidConfigBeginPacket(
                robotMode = stateStore.state.value.currentMode,
                safetyState = stateStore.state.value.safety,
                beginPayload = payload,
            )
            return sendWithRetry(
                StepRequest(
                    packet = packet,
                    expectedPacketType = PacketType.RFID_CONFIG_BEGIN.wireValue,
                    expectedRecordIndex = RECORD_INDEX_ALL,
                    sessionId = sessionId,
                    stage = RfidSyncStage.BEGIN,
                    waitingState = RfidSyncState.WaitingBeginAck,
                    sendingState = RfidSyncState.SendingBegin,
                ),
            )
        }

        private suspend fun transmitAllItemSteps(
            cityUids: Map<City, RfidUid>,
            sessionId: Int,
        ): Boolean {
            val orderedCities = listOf(City.SINOP, City.NIGDE, City.TOKAT, City.AYDIN, City.ELAZIG)
            val stages = listOf(
                RfidSyncStage.ITEM_0,
                RfidSyncStage.ITEM_1,
                RfidSyncStage.ITEM_2,
                RfidSyncStage.ITEM_3,
                RfidSyncStage.ITEM_4,
            )
            var allOk = true
            for ((index, city) in orderedCities.withIndex()) {
                if (allOk) {
                    allOk = transmitSingleItemStep(
                        city = city,
                        uid = cityUids.getValue(city),
                        index = index,
                        sessionId = sessionId,
                        stage = stages[index],
                    )
                }
            }
            return allOk
        }

        private suspend fun transmitSingleItemStep(
            city: City,
            uid: RfidUid,
            index: Int,
            sessionId: Int,
            stage: RfidSyncStage,
        ): Boolean {
            _syncState.value = RfidSyncState.SendingItem(index)
            val payload = RfidConfigItemPayload(
                sessionId = sessionId,
                tableVersion = 1,
                recordIndex = index,
                cityCode = city.code,
                uidLength = uid.uidLength,
                rawUidBytes = uid.rawUidBytes,
                enabled = true,
            )
            val packet = packetFactory.createRfidConfigItemPacket(
                robotMode = stateStore.state.value.currentMode,
                safetyState = stateStore.state.value.safety,
                itemPayload = payload,
            )
            return sendWithRetry(
                StepRequest(
                    packet = packet,
                    expectedPacketType = PacketType.RFID_CONFIG_ITEM.wireValue,
                    expectedRecordIndex = index,
                    sessionId = sessionId,
                    stage = stage,
                    waitingState = RfidSyncState.WaitingItemAck(index),
                    sendingState = RfidSyncState.SendingItem(index),
                ),
            )
        }

        private suspend fun transmitCommitStep(sessionId: Int, checksum: Long): Boolean {
            _syncState.value = RfidSyncState.SendingCommit
            val payload = RfidConfigCommitPayload(
                sessionId = sessionId,
                tableVersion = 1,
                recordCount = 5,
                tableChecksum = checksum,
            )
            val packet = packetFactory.createRfidConfigCommitPacket(
                robotMode = stateStore.state.value.currentMode,
                safetyState = stateStore.state.value.safety,
                commitPayload = payload,
            )
            return sendWithRetry(
                StepRequest(
                    packet = packet,
                    expectedPacketType = PacketType.RFID_CONFIG_COMMIT.wireValue,
                    expectedRecordIndex = RECORD_INDEX_ALL,
                    sessionId = sessionId,
                    stage = RfidSyncStage.COMMIT,
                    waitingState = RfidSyncState.WaitingCommitAck,
                    sendingState = RfidSyncState.SendingCommit,
                ),
            )
        }

        private suspend fun sendWithRetry(req: StepRequest): Boolean {
            var success = false
            var finished = false
            for (attempt in 1..MAX_ATTEMPTS) {
                if (!finished) {
                    _syncState.value = req.sendingState
                    commandQueue.enqueue(req.packet, PacketPriority.STANDARD, "RFID Sync ${req.stage} ($attempt)")

                    _syncState.value = req.waitingState
                    val response = waitForAckOrNack(req.sessionId, req.expectedPacketType, req.expectedRecordIndex)
                    val handled = handleResponseResult(response, req.stage, attempt)
                    if (handled != null) {
                        success = handled
                        finished = true
                    }
                }
            }
            return success
        }

        private fun handleResponseResult(
            result: ResponseResult,
            stage: RfidSyncStage,
            attempt: Int,
        ): Boolean? = when (result) {
            is ResponseResult.Ack -> true
            is ResponseResult.Nack -> {
                _syncState.value = RfidSyncState.Failed(
                    errorMessage = "Robot NACK cevabı döndü: ${result.nackPayload.error.description}",
                    errorCode = result.nackPayload.errorCode,
                    stage = stage,
                    attemptCount = attempt,
                )
                false
            }
            is ResponseResult.Timeout -> {
                logger.w(LogTag.COMMUNICATION, "Timeout waiting ACK for $stage (Attempt $attempt/$MAX_ATTEMPTS)")
                if (attempt == MAX_ATTEMPTS) {
                    _syncState.value = RfidSyncState.Failed(
                        errorMessage = "Robot yanıt vermedi (Zaman aşımı: $stage).",
                        errorCode = RfidNackErrorCode.TIMEOUT.code,
                        stage = stage,
                        attemptCount = MAX_ATTEMPTS,
                    )
                    false
                } else {
                    null
                }
            }
        }

        private suspend fun waitForAckOrNack(
            expectedSessionId: Int,
            expectedPacketType: Int,
            expectedRecordIndex: Int,
        ): ResponseResult {
            val startTime = System.currentTimeMillis()
            var matched: ResponseResult? = null
            while (matched == null) {
                val remaining = TIMEOUT_MS - (System.currentTimeMillis() - startTime)
                if (remaining <= 0) {
                    matched = ResponseResult.Timeout
                } else {
                    val packet = withTimeoutOrNull(remaining) { incomingRfidPackets.receive() }
                    matched = if (packet == null) {
                        ResponseResult.Timeout
                    } else {
                        checkPacketMatch(packet, expectedSessionId, expectedPacketType, expectedRecordIndex)
                    }
                }
            }
            return matched
        }

        private fun checkPacketMatch(
            packet: Packet,
            expectedSessionId: Int,
            expectedPacketType: Int,
            expectedRecordIndex: Int,
        ): ResponseResult? = when (packet.type) {
            PacketType.RFID_CONFIG_ACK -> {
                val ack = RfidConfigAckPayload.fromPayload(packet.payload)
                val validSession = validateAckSession(ack, expectedSessionId)
                val validType = validateAckType(ack, expectedPacketType)
                val validIndex = validateRecordIndex(ack.recordIndex, expectedRecordIndex)
                if (validSession && validType && validIndex) ResponseResult.Ack(ack) else null
            }
            PacketType.RFID_CONFIG_NACK -> {
                val nack = RfidConfigNackPayload.fromPayload(packet.payload)
                if (nack.sessionId == expectedSessionId) ResponseResult.Nack(nack) else null
            }
            else -> null
        }

        private fun validateAckSession(ack: RfidConfigAckPayload, expectedSessionId: Int): Boolean =
            ack.sessionId == expectedSessionId

        private fun validateAckType(ack: RfidConfigAckPayload, expectedPacketType: Int): Boolean =
            ack.acknowledgedPacketType == expectedPacketType

        private fun validateRecordIndex(actualIndex: Int, expectedIndex: Int): Boolean =
            expectedIndex == RECORD_INDEX_ALL || actualIndex == expectedIndex
    }
