package jp.awabi2048.cccontent.features.cooking

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.ContentCookingGeneratedKeys
import org.bukkit.Material
import kotlin.math.roundToInt

/** 液体の見た目へ変換するための内部色です。GUI上では色名やRGB値を表示しません。 */
internal data class CookingLiquidColor(
    val red: Int,
    val green: Int,
    val blue: Int
) {
    init {
        require(red in 0..255 && green in 0..255 && blue in 0..255)
    }

    fun distanceSquared(other: CookingLiquidColor): Int =
        (red - other.red) * (red - other.red) +
            (green - other.green) * (green - other.green) +
            (blue - other.blue) * (blue - other.blue)
}

internal data class CookingLiquidDefinition(
    val nameKey: LocalizationKey<String>,
    val color: CookingLiquidColor
)

/**
 * 液体ID、表示名、色、パネル素材の対応を一元管理します。
 *
 * 混合液は構成液体の量から色を加重平均し、Minecraftの色付きガラスパネルへ
 * 量子化します。表示処理が液体IDごとの分岐を増やさないようにするための層です。
 */
internal object CookingLiquidPresentation {
    private val definitions = linkedMapOf(
        CookingLiquidIds.WATER to CookingLiquidDefinition(
            ContentCookingGeneratedKeys.COOKING_LIQUID_WATER,
            CookingLiquidColor(63, 118, 228)
        ),
        CookingLiquidIds.SEA_WATER to CookingLiquidDefinition(
            ContentCookingGeneratedKeys.COOKING_LIQUID_SEA_WATER,
            CookingLiquidColor(35, 105, 180)
        ),
        CookingLiquidIds.SOY_MILK to CookingLiquidDefinition(
            ContentCookingGeneratedKeys.COOKING_LIQUID_SOY_MILK,
            CookingLiquidColor(245, 235, 205)
        ),
        CookingLiquidIds.BITTERN to CookingLiquidDefinition(
            ContentCookingGeneratedKeys.COOKING_LIQUID_BITTERN,
            CookingLiquidColor(92, 92, 92)
        )
    )

    private val paneColors = linkedMapOf(
        Material.WHITE_STAINED_GLASS_PANE to CookingLiquidColor(255, 255, 255),
        Material.LIGHT_GRAY_STAINED_GLASS_PANE to CookingLiquidColor(157, 157, 151),
        Material.GRAY_STAINED_GLASS_PANE to CookingLiquidColor(84, 84, 84),
        Material.BLACK_STAINED_GLASS_PANE to CookingLiquidColor(29, 29, 33),
        Material.BROWN_STAINED_GLASS_PANE to CookingLiquidColor(125, 84, 53),
        Material.RED_STAINED_GLASS_PANE to CookingLiquidColor(176, 46, 38),
        Material.ORANGE_STAINED_GLASS_PANE to CookingLiquidColor(240, 118, 19),
        Material.YELLOW_STAINED_GLASS_PANE to CookingLiquidColor(249, 199, 36),
        Material.LIME_STAINED_GLASS_PANE to CookingLiquidColor(112, 185, 25),
        Material.GREEN_STAINED_GLASS_PANE to CookingLiquidColor(94, 124, 22),
        Material.CYAN_STAINED_GLASS_PANE to CookingLiquidColor(21, 137, 145),
        Material.LIGHT_BLUE_STAINED_GLASS_PANE to CookingLiquidColor(58, 175, 218),
        Material.BLUE_STAINED_GLASS_PANE to CookingLiquidColor(53, 57, 157),
        Material.PURPLE_STAINED_GLASS_PANE to CookingLiquidColor(137, 50, 184),
        Material.MAGENTA_STAINED_GLASS_PANE to CookingLiquidColor(190, 68, 149),
        Material.PINK_STAINED_GLASS_PANE to CookingLiquidColor(238, 141, 174)
    )

    val knownLiquidIds: Set<String> = definitions.keys

    fun definition(liquidId: String): CookingLiquidDefinition =
        definitions[liquidId] ?: error("Unknown cooking liquid definition: $liquidId")

    fun orderedLiquidIds(contents: CookingLiquidContents): List<String> =
        definitions.keys.filter { contents.amount(it) > 0 }

    fun paneFor(contents: CookingLiquidContents): Material {
        if (contents.isEmpty()) return Material.WHITE_STAINED_GLASS_PANE
        val color = blendColor(contents)
        return paneColors.minBy { (_, paneColor) -> color.distanceSquared(paneColor) }.key
    }

    private fun blendColor(contents: CookingLiquidContents): CookingLiquidColor {
        val total = contents.total
        val red = contents.amounts.entries.sumOf { (id, amount) -> definition(id).color.red * amount }
        val green = contents.amounts.entries.sumOf { (id, amount) -> definition(id).color.green * amount }
        val blue = contents.amounts.entries.sumOf { (id, amount) -> definition(id).color.blue * amount }
        return CookingLiquidColor(
            (red.toDouble() / total).roundToInt(),
            (green.toDouble() / total).roundToInt(),
            (blue.toDouble() / total).roundToInt()
        )
    }
}
