package com.robolig.controller.domain.model

class RfidUid private constructor(bytes: ByteArray) {
    private val rawBytes: ByteArray = bytes.copyOf()

    val rawUidBytes: ByteArray
        get() = rawBytes.copyOf()

    val displayUid: String
        get() = rawBytes.joinToString("") { "%02X".format(it) }

    val uidLength: Int
        get() = rawBytes.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RfidUid
        if (uidLength != other.uidLength) return false
        return rawBytes.contentEquals(other.rawBytes)
    }

    override fun hashCode(): Int {
        var result = rawBytes.contentHashCode()
        result = 31 * result + uidLength
        return result
    }

    override fun toString(): String {
        return displayUid
    }

    companion object {
        private val VALID_LENGTHS = setOf(4, 7, 10) // lengths in bytes

        fun create(input: String): Result<RfidUid> {
            return runCatching {
                val normalized =
                    input.trim().replace(Regex("\\s+"), "")
                        .replace(":", "")
                        .replace("-", "")
                        .uppercase()

                require(normalized.isNotEmpty()) { "UID cannot be empty" }
                require(normalized.matches(Regex("^[0-9A-F]+\$"))) { "UID can only contain hex characters (0-9, A-F)" }
                require(normalized.length % 2 == 0) { "UID cannot have an odd number of hexadecimal characters" }

                val byteCount = normalized.length / 2
                require(byteCount in VALID_LENGTHS) { "UID must be 4, 7, or 10 bytes (8, 14, or 20 hex characters)" }

                val rawBytes = ByteArray(byteCount)
                for (i in 0 until byteCount) {
                    val hexByte = normalized.substring(i * 2, i * 2 + 2)
                    rawBytes[i] = hexByte.toInt(16).toByte()
                }

                RfidUid(rawBytes)
            }
        }

        fun isValid(input: String): Boolean {
            return create(input).isSuccess
        }
    }
}
