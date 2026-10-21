package com.ishan.mk1300

object RgbPacketBuilder {
    fun buildPacket(
        mode: Int,
        brightness: Int,
        speed: Int,
        hue: Int,
        saturation: Int,
        value: Int
    ): ByteArray {
        val lightConfig = byteArrayOf(
            1, 0, mode.toByte(), brightness.toByte(), speed.toByte(),
            1, 1, 0, hue.toByte(), saturation.toByte(), value.toByte()
        )
        val payload = byteArrayOf(11, lightConfig.size.toByte(), 0, 0) + lightConfig
        val cmd = byteArrayOf(6) + payload
        val buffer = ByteArray(64)
        cmd.copyInto(buffer)
        return buffer
    }
}
