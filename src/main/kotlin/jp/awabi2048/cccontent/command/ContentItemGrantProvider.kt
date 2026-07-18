package jp.awabi2048.cccontent.command

import com.awabi2048.ccsystem.api.item.ItemGrantDefinition
import com.awabi2048.ccsystem.api.item.ItemGrantProvider
import com.awabi2048.ccsystem.api.item.ItemGrantRequest
import com.awabi2048.ccsystem.api.item.ItemGrantResult
import jp.awabi2048.cccontent.items.CustomItemManager
import jp.awabi2048.cccontent.items.arena.ArenaEnchantShardItem
import jp.awabi2048.cccontent.items.arena.ArenaEnchantShardRegistry
import jp.awabi2048.cccontent.items.arena.ArenaMobTokenItem

class ContentItemGrantProvider : ItemGrantProvider {
    override val owner: String = "cc-content"

    override fun definitions(): Collection<ItemGrantDefinition> {
        val standard = CustomItemManager.getAllItemIds()
            .filterNot { it == "arena.enchant_shard" || it == "arena.mob_token" }
            .map { ItemGrantDefinition(it, "cc.item.give.cc-content", 64) { emptyList() } }
        val tokens = ArenaMobTokenItem.supportedTokenCategoryTypeIds()
            .map { ItemGrantDefinition("arena.mob_token:$it", "cc.item.give.cc-content", 64) { emptyList() } }
        val shards = ArenaEnchantShardRegistry.supportedSpecs()
            .map { ItemGrantDefinition("arena.enchant_shard:$it", "cc.item.give.cc-content", 64) { emptyList() } }
        return standard + tokens + shards
    }

    override fun grant(request: ItemGrantRequest): ItemGrantResult {
        val id = request.definition.id
        val items = when {
            id.startsWith("arena.enchant_shard:") -> {
                val definition = ArenaEnchantShardRegistry.findBySpec(id.substringAfter(':'))
                    ?: return ItemGrantResult(false, 0, 0, "unknown enchant shard")
                List(request.amount) {
                    ArenaEnchantShardItem.createShard(request.target, definition, 1)
                }
            }
            id.startsWith("arena.mob_token:") -> {
                val category = ArenaMobTokenItem.resolveTokenCategoryTypeId(id.substringAfter(':'))
                listOf(
                    CustomItemManager.createItemForPlayer(
                        ArenaMobTokenItem(category),
                        request.target,
                        request.amount
                    )
                )
            }
            else -> {
                val item = CustomItemManager.createItemForPlayer(id, request.target, request.amount)
                    ?: return ItemGrantResult(false, 0, 0, "unknown item id")
                listOf(item)
            }
        }

        var dropped = 0
        items.forEach { item ->
            request.target.inventory.addItem(item).values.forEach { overflow ->
                dropped += overflow.amount
                request.target.world.dropItemNaturally(request.target.location, overflow)
            }
        }
        val granted = (request.amount - dropped).coerceAtLeast(0)
        return ItemGrantResult(true, granted, dropped, null)
    }
}
