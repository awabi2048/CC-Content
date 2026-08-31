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
}

/** 液体を1回分回収するときの容器と、釜から減らす論理構成です。 */
internal data class CookingLiquidRecovery(
    val customItemId: String,
    val containerMaterial: Material,
    val consumedAmounts: Map<String, Int>
) {
    init {
        require(customItemId.isNotBlank())
        require(consumedAmounts.isNotEmpty() && consumedAmounts.values.all { it > 0 })
    }

    val consumedUnits: Int = consumedAmounts.values.sum()
}

internal data class CookingLiquidDefinition(
    val nameKey: LocalizationKey<String>,
    val color: CookingLiquidColor,
    val collectable: Boolean,
    val recovery: CookingLiquidRecovery? = null
) {
    init {
        require(!collectable || recovery != null) {
            "A collectable cooking liquid must define a recovery contract"
        }
    }
}

/**
 * 液体ID、表示名、色、回収可否の対応を一元管理します。
 *
 * 混合液は構成液体の量から色を加重平均します。未登録の混合液は表示名と色を
 * 構成から生成できますが、意味のある液体として登録されていないため回収不可です。
 * 将来、回収可能な混合液を追加するときは mixtureDefinitions に1回分の構成・表示名・
 * 回収契約を登録するだけで、画面と回収処理が同じ定義を参照できます。複数回分の構成も
 * 同じキー集合であれば、1回分ずつ回収できます。
 */
internal object CookingLiquidPresentation {
    private val definitions = linkedMapOf(
        CookingLiquidIds.WATER to CookingLiquidDefinition(
            ContentCookingGeneratedKeys.COOKING_LIQUID_WATER,
            CookingLiquidColor(63, 118, 228),
            collectable = false
        ),
        CookingLiquidIds.SEA_WATER to CookingLiquidDefinition(
            ContentCookingGeneratedKeys.COOKING_LIQUID_SEA_WATER,
            CookingLiquidColor(35, 105, 180),
            collectable = true,
            recovery = CookingLiquidRecovery(
                CookingLiquidIds.SEA_WATER_BUCKET,
                Material.BUCKET,
                mapOf(CookingLiquidIds.SEA_WATER to CookingLiquidVolume.UNITS_PER_CAULDRON)
            )
        ),
        CookingLiquidIds.SOY_MILK to CookingLiquidDefinition(
            ContentCookingGeneratedKeys.COOKING_LIQUID_SOY_MILK,
            CookingLiquidColor(245, 235, 205),
            collectable = true,
            recovery = CookingLiquidRecovery(
                CookingLiquidIds.SOY_MILK_BOTTLE,
                Material.GLASS_BOTTLE,
                mapOf(CookingLiquidIds.SOY_MILK to 1)
            )
        ),
        CookingLiquidIds.BITTERN to CookingLiquidDefinition(
            ContentCookingGeneratedKeys.COOKING_LIQUID_BITTERN,
            CookingLiquidColor(92, 92, 92),
            collectable = true,
            recovery = CookingLiquidRecovery(
                CookingLiquidIds.NIGARI_BOTTLE,
                Material.GLASS_BOTTLE,
                mapOf(CookingLiquidIds.BITTERN to 1)
            )
        )
    )

    /** 将来の意味のある混合液を、1回分の構成Mapをキーとして追加する拡張点です。 */
    private val mixtureDefinitions: Map<Map<String, Int>, CookingLiquidDefinition> = emptyMap()

    val knownLiquidIds: Set<String> = definitions.keys

    fun definition(liquidId: String): CookingLiquidDefinition =
        definitions[liquidId] ?: error("Unknown cooking liquid definition: $liquidId")

    fun definitionFor(contents: CookingLiquidContents): CookingLiquidDefinition? = when {
        contents.isEmpty() -> null
        contents.amounts.size == 1 -> definitions[contents.amounts.keys.single()]
        else -> mixtureDefinitions.entries
            .filter { (components, _) ->
                contents.amounts.keys == components.keys && contents.canMinus(components)
            }
            .maxByOrNull { (components, _) -> components.values.sum() }
            ?.value
    }

    fun isCollectable(contents: CookingLiquidContents): Boolean =
        definitionFor(contents)?.collectable == true

    fun recoveryFor(contents: CookingLiquidContents): CookingLiquidRecovery? =
        definitionFor(contents)?.takeIf { it.collectable }?.recovery

    fun orderedLiquidIds(contents: CookingLiquidContents): List<String> =
        definitions.keys.filter { contents.amount(it) > 0 }

    /** 登録済みの意味ある混合液は固有色を優先し、それ以外は構成量から合成します。 */
    fun colorFor(contents: CookingLiquidContents): CookingLiquidColor =
        definitionFor(contents)?.color ?: blendColor(contents)

    private fun blendColor(contents: CookingLiquidContents): CookingLiquidColor {
        if (contents.isEmpty()) return CookingLiquidColor(255, 255, 255)
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
