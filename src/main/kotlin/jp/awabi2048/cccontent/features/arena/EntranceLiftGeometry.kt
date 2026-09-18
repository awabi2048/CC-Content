package jp.awabi2048.cccontent.features.arena

/**
 * エントランスリフトの向き対応に関する純粋計算の集約。
 * 横モードでは footprint を90°回転させて扱う。プレビュー側の回転判定と一致させる。
 * Bukkit 依存を持たないため単体テストで検証する。
 */
object EntranceLiftGeometry {
    const val LIFT_TAG = "arena.marker.lift"
    const val LIFT_HORIZONTAL_TAG = "arena.marker.lift.horizontal"

    /** 横モードの貼付回転量（時計回り90°）。寸法上は90°/270°同値のため固定する。 */
    const val HORIZONTAL_ROTATION_QUARTER = 1

    fun isHorizontal(tags: Set<String>): Boolean = LIFT_HORIZONTAL_TAG in tags

    fun rotationQuarter(horizontal: Boolean): Int =
        if (horizontal) HORIZONTAL_ROTATION_QUARTER else 0

    fun footprintSizeX(sizeX: Int, sizeZ: Int, horizontal: Boolean): Int =
        if (horizontal) sizeZ else sizeX

    fun footprintSizeZ(sizeX: Int, sizeZ: Int, horizontal: Boolean): Int =
        if (horizontal) sizeX else sizeZ
}
