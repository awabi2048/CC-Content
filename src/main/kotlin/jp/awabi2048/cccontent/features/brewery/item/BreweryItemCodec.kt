@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.features.brewery.item

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import jp.awabi2048.cccontent.features.brewery.BreweryRecipe
import jp.awabi2048.cccontent.features.brewery.breweryQualityIndex
import jp.awabi2048.cccontent.features.brewery.breweryQualityTier
import jp.awabi2048.cccontent.features.brewery.model.BrewStage
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData
import kotlin.math.roundToInt

class BreweryItemCodec(private val plugin: JavaPlugin) {
    private val schemaVersionKey = NamespacedKey(plugin, "brewery_schema_version")
    private val stageKey = NamespacedKey(plugin, "brewery_stage")
    private val recipeIdKey = NamespacedKey(plugin, "brewery_recipe_id")
    private val qualityKey = NamespacedKey(plugin, "brewery_quality")
    private val alcoholKey = NamespacedKey(plugin, "brewery_alcohol")
    private val distillCountKey = NamespacedKey(plugin, "brewery_distill_count")
    private val muddyKey = NamespacedKey(plugin, "brewery_muddy")
    private val ambiguousKey = NamespacedKey(plugin, "brewery_ambiguous")
    private val historyKey = NamespacedKey(plugin, "brewery_history")
    private val agingStartKey = NamespacedKey(plugin, "brewery_aging_started_at")
    private val finalStarsKey = NamespacedKey(plugin, "brewery_final_stars")
    private val filterKey = NamespacedKey(plugin, "brewery_filter_sample")
    private val filterRemainingUsesKey = NamespacedKey(plugin, "brewery_filter_remaining_uses")
    private val expAwardedKey = NamespacedKey(plugin, "brewery_stage_exp_awarded")
    private val customItemIdKey = NamespacedKey("cccontent", "custom_item_id")

    data class BreweryItemState(
        val stage: BrewStage,
        val recipeId: String,
        val quality: Double,
        val alcohol: Double,
        val distillCount: Int,
        val muddy: Boolean,
        val ambiguous: Boolean,
        val history: String,
        val agingStartedAt: Long,
        val finalStars: Int
    )

    fun createFermentedBottle(recipeId: String, quality: Double, muddy: Boolean, history: String, recipe: BreweryRecipe, player: Player?): ItemStack {
        val item = ItemStack(Material.POTION)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text(text(player, if (muddy) "brewery.item.name.muddy" else "brewery.item.name.fermented",
            "recipe" to text(player, "brewery.recipe.$recipeId.name"),
            "product" to text(player, "brewery.recipe.$recipeId.middle.name"))))
        meta.lore(renderLore(player, if (muddy) "" else text(player, "brewery.recipe.$recipeId.middle.description"), listOf(
            "brewery.item.data.recipe" to text(player, "brewery.recipe.$recipeId.name"),
            "brewery.item.data.stage" to text(player, if (muddy) "brewery.item.stage.failed" else "brewery.item.stage.fermented"),
            "brewery.item.data.quality" to "%.1f".format(quality)
        )))
        val pdc = meta.persistentDataContainer
        pdc.set(schemaVersionKey, PersistentDataType.INTEGER, 2)
        pdc.set(stageKey, PersistentDataType.STRING, if (muddy) BrewStage.FAILED.name else BrewStage.FERMENTED.name)
        pdc.set(recipeIdKey, PersistentDataType.STRING, recipeId)
        pdc.set(qualityKey, PersistentDataType.DOUBLE, quality)
        pdc.set(alcoholKey, PersistentDataType.DOUBLE, 0.0)
        pdc.set(distillCountKey, PersistentDataType.INTEGER, 0)
        pdc.set(muddyKey, PersistentDataType.BYTE, if (muddy) 1 else 0)
        pdc.set(ambiguousKey, PersistentDataType.BYTE, 0)
        pdc.set(historyKey, PersistentDataType.STRING, history)
        pdc.set(finalStarsKey, PersistentDataType.INTEGER, 0)
        pdc.set(customItemIdKey, PersistentDataType.STRING, "brewery.product.$recipeId")
        if (!muddy) applyPotionColor(meta, recipe.middleOutputColor)
        item.itemMeta = meta
        return item
    }

    private fun applyPotionColor(meta: org.bukkit.inventory.meta.ItemMeta, colorHex: String?) {
        val potionMeta = meta as? org.bukkit.inventory.meta.PotionMeta ?: return
        parseHexColor(colorHex)?.let(potionMeta::setColor)
    }

    private fun parseHexColor(hex: String?): Color? {
        val clean = hex?.removePrefix("#") ?: return null
        if (clean.length != 6) return null
        return runCatching { Color.fromRGB(clean.substring(0, 2).toInt(16), clean.substring(2, 4).toInt(16), clean.substring(4, 6).toInt(16)) }.getOrNull()
    }

    fun parse(item: ItemStack?): BreweryItemState? {
        if (item == null || item.type.isAir) return null
        val pdc = item.itemMeta?.persistentDataContainer ?: return null
        if (pdc.get(schemaVersionKey, PersistentDataType.INTEGER) != 2) return null
        val stage = pdc.get(stageKey, PersistentDataType.STRING)?.let { runCatching { BrewStage.valueOf(it) }.getOrNull() } ?: return null
        val recipeId = pdc.get(recipeIdKey, PersistentDataType.STRING)?.takeIf { it.isNotBlank() } ?: return null
        return BreweryItemState(stage, recipeId,
            pdc.get(qualityKey, PersistentDataType.DOUBLE) ?: 0.0,
            pdc.get(alcoholKey, PersistentDataType.DOUBLE) ?: 0.0,
            pdc.get(distillCountKey, PersistentDataType.INTEGER) ?: 0,
            (pdc.get(muddyKey, PersistentDataType.BYTE)?.toInt() ?: 0) == 1,
            (pdc.get(ambiguousKey, PersistentDataType.BYTE)?.toInt() ?: 0) == 1,
            pdc.get(historyKey, PersistentDataType.STRING) ?: "",
            pdc.get(agingStartKey, PersistentDataType.LONG) ?: 0L,
            pdc.get(finalStarsKey, PersistentDataType.INTEGER) ?: 0)
    }

    fun markDistilled(item: ItemStack, state: BreweryItemState, targetDistillCount: Int, overPenalty: Double, recipe: BreweryRecipe, player: Player?) {
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        val over = if (targetDistillCount <= 0) 0 else (state.distillCount - targetDistillCount).coerceAtLeast(0)
        val quality = (state.quality - over * overPenalty).coerceIn(0.0, 100.0)
        val ambiguous = state.distillCount != targetDistillCount
        pdc.set(stageKey, PersistentDataType.STRING, BrewStage.DISTILLED.name)
        pdc.set(qualityKey, PersistentDataType.DOUBLE, quality)
        pdc.set(ambiguousKey, PersistentDataType.BYTE, if (ambiguous) 1 else 0)
        val tier = breweryQualityTier(quality)
        val product = text(player, "brewery.recipe.${recipe.id}.final.$tier.name")
        meta.displayName(Component.text(text(player, if (ambiguous) "brewery.item.name.distilled_degraded" else "brewery.item.name.distilled", "recipe" to text(player, "brewery.recipe.${recipe.id}.name"), "product" to product)))
        meta.lore(renderLore(player, text(player, "brewery.recipe.${recipe.id}.final.$tier.description"), listOf(
            "brewery.item.data.recipe" to text(player, "brewery.recipe.${recipe.id}.name"),
            "brewery.item.data.stage" to text(player, "brewery.item.stage.distilled"),
            "brewery.item.data.quality" to "%.1f".format(quality),
            "brewery.item.data.distill_count" to "${state.distillCount}/$targetDistillCount",
            "brewery.item.data.alcohol" to "%.1f%%".format(state.alcohol)
        )))
        applyPotionColor(meta, recipe.finalOutputColor)
        item.itemMeta = meta
    }

    fun incrementDistillation(item: ItemStack, targetDistillCount: Int, targetAlcohol: Double) {
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        val count = (pdc.get(distillCountKey, PersistentDataType.INTEGER) ?: 0) + 1
        val alcohol = pdc.get(alcoholKey, PersistentDataType.DOUBLE) ?: 0.0
        val next = if (count <= targetDistillCount && targetDistillCount > 0) alcohol + targetAlcohol / targetDistillCount else alcohol + 5.0
        pdc.set(distillCountKey, PersistentDataType.INTEGER, count)
        pdc.set(alcoholKey, PersistentDataType.DOUBLE, next.coerceIn(0.0, 100.0))
        item.itemMeta = meta
    }

    fun setAgingStart(item: ItemStack, startedAt: Long) { item.editMeta { it.persistentDataContainer.set(agingStartKey, PersistentDataType.LONG, startedAt) } }
    fun clearAgingStart(item: ItemStack) { item.editMeta { it.persistentDataContainer.remove(agingStartKey) } }

    fun markAged(item: ItemStack, state: BreweryItemState, finalQuality: Double, recipe: BreweryRecipe, player: Player?) {
        val rounded = (finalQuality / 20.0).roundToInt().coerceIn(0, 5) * 20
        val stars = (rounded / 20).coerceIn(1, 5)
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        pdc.set(stageKey, PersistentDataType.STRING, BrewStage.AGED.name)
        pdc.set(qualityKey, PersistentDataType.DOUBLE, finalQuality.coerceIn(0.0, 100.0))
        val correctedAlcohol =
            jp.awabi2048.cccontent.features.brewery.BreweryIntoxicationMath
                .qualityCorrectedAlcohol(recipe.finalOutputAlcohol, finalQuality)
        pdc.set(alcoholKey, PersistentDataType.DOUBLE, correctedAlcohol)
        pdc.set(finalStarsKey, PersistentDataType.INTEGER, stars)
        val tier = breweryQualityTier(finalQuality)
        val product = text(player, "brewery.recipe.${recipe.id}.final.$tier.name")
        meta.displayName(Component.text(text(player, "brewery.item.name.aged", "recipe" to text(player, "brewery.recipe.${recipe.id}.name"), "product" to product, "stars" to "★".repeat(stars))))
        meta.setEnchantmentGlintOverride(recipe.finalOutputGlint)
        meta.lore(renderLore(player, text(player, "brewery.recipe.${recipe.id}.final.$tier.description"), listOf(
            "brewery.item.data.recipe" to text(player, "brewery.recipe.${recipe.id}.name"),
            "brewery.item.data.stage" to text(player, "brewery.item.stage.aged"),
            "brewery.item.data.final_quality" to "%.1f".format(finalQuality.coerceIn(0.0, 100.0)),
            "brewery.item.data.rating" to "★".repeat(stars),
            "brewery.item.data.alcohol" to "%.1f%%".format(correctedAlcohol)
        )))
        applyPotionColor(meta, recipe.finalOutputColor)
        item.itemMeta = meta
        recipe.finalOutputCustomModelData.getOrNull(breweryQualityIndex(finalQuality))?.let { model ->
            item.setData(
                DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelData.customModelData().addFloat(model.toFloat()).build()
            )
        }
    }

    fun hasStageExpAwarded(item: ItemStack, stage: BrewStage): Boolean =
        ((item.itemMeta?.persistentDataContainer?.get(expAwardedKey, PersistentDataType.INTEGER) ?: 0) and (1 shl stage.ordinal)) != 0

    fun markStageExpAwarded(item: ItemStack, stage: BrewStage) = item.editMeta {
        val pdc = it.persistentDataContainer
        val mask = pdc.get(expAwardedKey, PersistentDataType.INTEGER) ?: 0
        pdc.set(expAwardedKey, PersistentDataType.INTEGER, mask or (1 shl stage.ordinal))
    }

    fun isSampleFilter(item: ItemStack?): Boolean = item?.type == Material.POISONOUS_POTATO && item.itemMeta?.persistentDataContainer?.has(filterKey, PersistentDataType.BYTE) == true

    fun buildSampleFilterItem(player: Player?): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO)
        val meta = item.itemMeta ?: return item
        meta.setItemModel(NamespacedKey.minecraft("shears"))
        meta.displayName(Component.text(text(player, "brewery.item.filter.name")))
        val lines = CCSystem.getAPI().getI18nStringList(player, "brewery.item.filter.description")
        meta.lore(CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(listOf(GuiLoreBlock(lines.map(GuiLoreLine::Text))))) )
        meta.persistentDataContainer.set(filterKey, PersistentDataType.BYTE, 1)
        meta.persistentDataContainer.set(filterRemainingUsesKey, PersistentDataType.INTEGER, FILTER_MAX_USES)
        item.itemMeta = meta
        return item
    }

    fun damageFilter(item: ItemStack, amount: Int = 1): Boolean {
        val meta = item.itemMeta ?: return true
        if (item.type == Material.POISONOUS_POTATO) {
            val remaining = meta.persistentDataContainer.get(filterRemainingUsesKey, PersistentDataType.INTEGER) ?: FILTER_MAX_USES
            val next = remaining - amount.coerceAtLeast(1)
            if (next <= 0) return true
            meta.persistentDataContainer.set(filterRemainingUsesKey, PersistentDataType.INTEGER, next)
            item.itemMeta = meta
            return false
        }
        val damageable = meta as? org.bukkit.inventory.meta.Damageable ?: return true
        damageable.damage += amount
        if (damageable.damage >= item.type.maxDurability) return true
        item.itemMeta = damageable
        return false
    }

    fun writeHistory(item: ItemStack, history: String) = item.editMeta { it.persistentDataContainer.set(historyKey, PersistentDataType.STRING, history) }
    fun getHistory(container: PersistentDataContainer): String = container.get(historyKey, PersistentDataType.STRING) ?: ""

    private fun text(player: Player?, key: String, vararg placeholders: Pair<String, Any?>): String =
        CCSystem.getAPI().getI18nString(player, key, placeholders.associate { it.first to (it.second ?: "") }).replace('&', '§')

    private fun renderLore(player: Player?, description: String, data: List<Pair<String, Any?>>): List<net.kyori.adventure.text.Component> {
        val blocks = mutableListOf<GuiLoreBlock>()
        if (description.isNotBlank()) blocks += GuiLoreBlock(listOf(GuiLoreLine.Text(description)))
        blocks += GuiLoreBlock(data.map { (key, value) -> GuiLoreLine.Data(text(player, key), value, "§f") })
        return CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(blocks))
    }

    companion object { private const val FILTER_MAX_USES = 238 }
}
