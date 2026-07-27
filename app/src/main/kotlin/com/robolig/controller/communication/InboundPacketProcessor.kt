package com.robolig.controller.communication

import com.robolig.controller.core.AppLogger
import com.robolig.controller.core.ApplicationScope
import com.robolig.controller.core.LogTag
import com.robolig.controller.domain.rfid.RfidSyncManager
import com.robolig.controller.protocol.Packet
import com.robolig.controller.protocol.PacketDecodeResult
import com.robolig.controller.protocol.PacketDecoder
import com.robolig.controller.protocol.PacketType
import com.robolig.controller.usb.UsbSerialManager
import com.robolig.controller.utils.MonotonicClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class InboundPacketProcessor
    @Inject
    constructor(
        private val usbSerialManager: UsbSerialManager,
        private val packetDecoder: PacketDecoder,
        private val heartbeatManager: HeartbeatManager,
        private val stateStore: CommunicationStateStore,
        private val clock: MonotonicClock,
        private val logger: AppLogger,
        private val rfidSyncManagerProvider: Provider<RfidSyncManager>,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        private var started = false

        fun start() {
            if (started) {
                return
            }
            started = true
            applicationScope.launch {
                usbSerialManager.incomingPackets.collectLatest { rawPacket ->
                    when (val decodeResult = packetDecoder.decode(rawPacket)) {
                        is PacketDecodeResult.Failure -> {
                            stateStore.update { currentState ->
                                currentState.copy(
                                    diagnostics =
                                        currentState.diagnostics.copy(
                                            invalidPackets = currentState.diagnostics.invalidPackets + 1,
                                        ),
                                    errors =
                                        when (decodeResult.reason) {
                                            com.robolig.controller.protocol.PacketValidationFailure.INVALID_SEQUENCE ->
                                                currentState.errors + "Duplicate or out-of-order packet rejected"
                                            else -> currentState.errors
                                        },
                                )
                            }
                        }

                        is PacketDecodeResult.Success -> {
                            val packet = decodeResult.packet
                            stateStore.update { currentState ->
                                currentState.copy(
                                    diagnostics =
                                        currentState.diagnostics.copy(
                                            packetsReceived = currentState.diagnostics.packetsReceived + 1,
                                            droppedPackets =
                                                currentState.diagnostics.droppedPackets +
                                                    decodeResult.droppedSequenceCount,
                                        ),
                                    safety =
                                        currentState.safety.copy(
                                            lastValidPacketAtMs = clock.elapsedRealtimeMs(),
                                            watchdogTriggered = false,
                                        ),
                                )
                            }
                            processPacket(packet)
                        }
                    }
                }
            }
        }

        private fun processPacket(packet: Packet) {
            when (packet.type) {
                PacketType.HEARTBEAT -> heartbeatManager.markReceived()
                PacketType.EMERGENCY_STOP ->
                    stateStore.update { currentState ->
                        currentState.copy(
                            safety = currentState.safety.copy(emergencyStopLatched = true),
                            errors = currentState.errors + "Robot reported emergency stop",
                        )
                    }
                PacketType.RFID_CONFIG_ACK, PacketType.RFID_CONFIG_NACK -> {
                    logger.d(LogTag.COMMUNICATION, "Inbound RFID response: ${packet.type}")
                    rfidSyncManagerProvider.get().onRfidPacketReceived(packet)
                }
                else -> Unit
            }
        }
    }
