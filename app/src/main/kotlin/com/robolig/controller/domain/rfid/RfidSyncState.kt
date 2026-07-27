package com.robolig.controller.domain.rfid

enum class RfidSyncStage {
    VALIDATING,
    BEGIN,
    ITEM_0,
    ITEM_1,
    ITEM_2,
    ITEM_3,
    ITEM_4,
    COMMIT,
}

sealed interface RfidSyncState {
    data object Idle : RfidSyncState

    data object Validating : RfidSyncState

    data object SendingBegin : RfidSyncState

    data object WaitingBeginAck : RfidSyncState

    data class SendingItem(val recordIndex: Int) : RfidSyncState

    data class WaitingItemAck(val recordIndex: Int) : RfidSyncState

    data object SendingCommit : RfidSyncState

    data object WaitingCommitAck : RfidSyncState

    data object Success : RfidSyncState

    data class Failed(
        val errorMessage: String,
        val errorCode: Int? = null,
        val stage: RfidSyncStage? = null,
        val attemptCount: Int = 1,
    ) : RfidSyncState

    data object Disconnected : RfidSyncState
}
