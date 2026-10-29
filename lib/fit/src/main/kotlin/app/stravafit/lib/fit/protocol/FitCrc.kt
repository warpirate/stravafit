// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.fit.protocol

/**
 * The 16-bit CRC used by FIT headers and files (reflected polynomial 0xA001, initial value 0,
 * i.e. CRC-16/ARC), computed one nibble at a time as the protocol describes.
 */
public object FitCrc {
    private val TABLE = intArrayOf(
        0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
        0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400,
    )

    /** Feeds one [byte] into [crc] and returns the updated value. */
    public fun update(crc: Int, byte: Int): Int {
        var c = crc
        var tmp = TABLE[c and 0xF]
        c = (c shr 4) and 0x0FFF
        c = c xor tmp xor TABLE[byte and 0xF]
        tmp = TABLE[c and 0xF]
        c = (c shr 4) and 0x0FFF
        c = c xor tmp xor TABLE[(byte shr 4) and 0xF]
        return c
    }

    /** CRC of `bytes[from until to]`. */
    public fun compute(bytes: ByteArray, from: Int = 0, to: Int = bytes.size): Int {
        var crc = 0
        for (i in from until to) crc = update(crc, bytes[i].toInt() and 0xFF)
        return crc
    }
}
