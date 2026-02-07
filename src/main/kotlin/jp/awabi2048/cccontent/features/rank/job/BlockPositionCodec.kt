package jp.awabi2048.cccontent.features.rank.job

object BlockPositionCodec {
    fun pack(x: Int, y: Int, z: Int): Long {
        val xPart = (x.toLong() and 0x3FFFFFFL) shl 38
        val zPart = (z.toLong() and 0x3FFFFFFL) shl 12
        val yPart = y.toLong() and 0xFFFL
        return xPart or zPart or yPart
    }

    fun unpack(packed: Long): Triple<Int, Int, Int> {
        val x = (packed shr 38).toInt()
        val y = (packed shl 52 shr 52).toInt()
        val z = (packed shl 26 shr 38).toInt()
        return Triple(x, y, z)
    }

    fun toBase36(packed: Long): String {
        return java.lang.Long.toUnsignedString(packed, 36)
    }
}
