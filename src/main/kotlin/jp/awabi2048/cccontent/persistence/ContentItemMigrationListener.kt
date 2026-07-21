@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.persistence

import jp.awabi2048.cccontent.items.CustomItemManager
import org.bukkit.Material
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerAttemptPickupItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.logging.Logger

/** 旧ItemStackをCC-Contentが接触した時点で正規形式へ変換する。 */
class ContentItemMigrationListener(private val logger: Logger) : Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) {
        event.player.inventory.contents.forEach(::migrate)
        event.player.inventory.armorContents.forEach(::migrate)
        migrate(event.player.inventory.itemInOffHand)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onOpen(event: InventoryOpenEvent) {
        event.inventory.contents.forEach(::migrate)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPickup(event: PlayerAttemptPickupItemEvent) {
        migrateEntity(event.item)
    }

    fun migrate(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir) return false
        val original = item.clone()
        return runCatching { migrateUnsafe(item) }
            .onFailure { failure ->
                item.type = original.type
                item.amount = original.amount
                item.itemMeta = original.itemMeta
                logger.warning("[ItemMigration] 旧アイテム変換失敗 type=${original.type}: ${failure.message}")
            }
            .getOrDefault(false)
    }

    private fun migrateEntity(entity: Item) {
        val stack = entity.itemStack
        if (migrate(stack)) entity.itemStack = stack
    }

    private fun migrateUnsafe(item: ItemStack): Boolean {
        val meta = item.itemMeta ?: return false
        var changed = false
        val customId = ContentPdcKeys.readAndMigrate(
            meta.persistentDataContainer,
            "custom_item_id",
            PersistentDataType.STRING,
            ::warnConflict
        ).also { changed = changed || it.changed }.value

        val gardenReplacement = gardenReplacement(customId, item.amount)
        if (gardenReplacement != null) {
            item.type = gardenReplacement.type
            item.amount = gardenReplacement.amount
            item.itemMeta = gardenReplacement.itemMeta
            return true
        }

        val fishId = ContentPdcKeys.readAndMigrate(
            meta.persistentDataContainer,
            "fish_id",
            PersistentDataType.STRING,
            ::warnConflict
        ).also { changed = changed || it.changed }.value
        if (fishId != null) {
            LEGACY_FISH_DETAIL_KEYS.forEach { key ->
                val current = ContentPdcKeys.current(key)
                val legacy = ContentPdcKeys.legacy(key)
                if (meta.persistentDataContainer.has(current) || meta.persistentDataContainer.has(legacy)) changed = true
                meta.persistentDataContainer.remove(current)
                meta.persistentDataContainer.remove(legacy)
            }
            meta.persistentDataContainer.set(ContentPdcKeys.fishItemSchema, PersistentDataType.INTEGER, 2)
            meta.setMaxStackSize(64)
            changed = true
        }

        listOf("resource_id", "resource_tags", "cooking_recipe_id", "cooking_stage",
            "brew_family_id", "brew_stage", "brew_quality", "brew_distill_count",
            "gyotaku_record_id", "item_schema_version").forEach { key ->
            migrateKnownKey(meta.persistentDataContainer, key).also { changed = changed || it }
        }
        if (changed) item.itemMeta = meta
        return changed
    }

    private fun migrateKnownKey(container: org.bukkit.persistence.PersistentDataContainer, key: String): Boolean {
        val stringValue = ContentPdcKeys.readAndMigrate(container, key, PersistentDataType.STRING, ::warnConflict)
        if (stringValue.changed || stringValue.value != null) return stringValue.changed
        return ContentPdcKeys.readAndMigrate(container, key, PersistentDataType.INTEGER, ::warnConflict).changed
    }

    private fun gardenReplacement(customId: String?, amount: Int): ItemStack? = when (customId) {
        "brewery.garden_seed_apple", "brewery.garden_fruit_apple" -> ItemStack(Material.APPLE, amount)
        "brewery.garden_seed_blueberry", "brewery.garden_fruit_blueberry" ->
            CustomItemManager.createItem("resource.blueberry", amount)
        "brewery.garden_seed_grape", "brewery.garden_fruit_grape" ->
            CustomItemManager.createItem("resource.grape", amount)
        "brewery.garden_seed_strawberry", "brewery.garden_fruit_strawberry" ->
            CustomItemManager.createItem("resource.strawberry", amount)
        else -> null
    }

    private fun warnConflict(key: String) {
        logger.warning("[PDC] cccontent:${key}とcc-content:${key}の値が異なるため新キーを優先します")
    }

    companion object {
        private val LEGACY_FISH_DETAIL_KEYS = listOf(
            "fish_weight_grams", "fish_size_cm", "fish_quality", "fisher_uuid",
            "fisher_name", "caught_at_millis", "caught_at_epoch_millis", "catch_record_id"
        )
    }
}
