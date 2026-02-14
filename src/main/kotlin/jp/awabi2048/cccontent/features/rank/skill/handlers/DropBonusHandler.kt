package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler
import jp.awabi2048.cccontent.features.rank.skill.listeners.BlockBreakEffectListener
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import java.util.UUID
import kotlin.math.floor
import kotlin.random.Random

/**
 * ドロップ増加スキルハンドラー
 *
 * パラメータ:
 *   fortune_level: Double - 追加の幸運レベル（小数可）
 *                         バニラの幸運と合算した値でドロップ数を計算
 *   targetBlocks: List<String> - 対象ブロック（オプション）
 *   targetItems: List<String> - 対象アイテム（オプション）
 *
 * 計算方式:
 *   幸運レベル = バニラ Fortune + スキル fortune_level の合算
 *   ドロップ数 = floor(元のドロップ数 × (1 + 幸運レベル × 0.5)) + 確率による追加ドロップ
 */
class DropBonusHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "collect.drop_bonus"

        private val FARMER_TARGET_BLOCKS = setOf(
            "WHEAT",
            "CARROTS",
            "POTATOES",
            "BEETROOTS",
            "MELON",
            "PUMPKIN",
            "COCOA",
            "NETHER_WART",
            "SUGAR_CANE",
            "CACTUS",
            "BAMBOO",
            "KELP",
            "SWEET_BERRY_BUSH"
        )

        private val FARMER_HARVEST_ITEMS = setOf(
            Material.WHEAT,
            Material.CARROT,
            Material.POTATO,
            Material.BEETROOT,
            Material.MELON_SLICE,
            Material.PUMPKIN,
            Material.COCOA_BEANS,
            Material.NETHER_WART,
            Material.SUGAR_CANE,
            Material.CACTUS,
            Material.BAMBOO,
            Material.KELP,
            Material.SWEET_BERRIES
        )
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.RUNTIME

    override fun applyEffect(context: EffectContext): Boolean {
        val event = context.getEvent<org.bukkit.event.block.BlockDropItemEvent>() ?: return false
        val player = event.player
        val profession = context.profession
        
        val fortuneLevel = context.skillEffect.getDoubleParam("fortune_level", 0.0)
        
        if (fortuneLevel <= 0) {
            return false
        }
        
        val blockType = resolveBlockType(event, player.uniqueId)
        
        // 職業別ブロック判定
        if (!isApplicableBlock(blockType, profession.id)) {
            return false
        }
        
        // プレイヤーが持つ道具のバニラ Fortune レベルを取得
        val toolInHand = player.inventory.itemInMainHand
        val vanillaFortuneLevel = toolInHand.getEnchantmentLevel(Enchantment.FORTUNE)
        
        // 総幸運レベル = バニラ + スキル
        val totalFortuneLevel = vanillaFortuneLevel + fortuneLevel
        
        val dropsToAdd = mutableListOf<org.bukkit.entity.Item>()
        val isFarmer = profession.id == "farmer"

        for (drop in event.items) {
            if (isFarmer && !isFarmerHarvestItem(drop.itemStack.type)) {
                continue
            }

            val originalAmount = drop.itemStack.amount
            val increasedAmount = calculateFortuneDrops(originalAmount, totalFortuneLevel)
            val additionalAmount = increasedAmount - originalAmount
            
            if (additionalAmount > 0) {
                val newItem = drop.itemStack.clone()
                newItem.amount = additionalAmount
                dropsToAdd.add(event.block.world.dropItemNaturally(event.block.location.add(0.5, 0.5, 0.5), newItem))
            }
        }

        if (dropsToAdd.isNotEmpty()) {
            org.bukkit.Bukkit.getLogger().info("[DropBonusHandler] ${player.name}: block=$blockType fortune=$totalFortuneLevel (vanilla=$vanillaFortuneLevel + skill=$fortuneLevel) -> added=${dropsToAdd.sumOf { it.itemStack.amount }}")
        }
        return dropsToAdd.isNotEmpty()
    }

    /**
     * 職業に応じた適用対象ブロック判定
     */
    private fun isApplicableBlock(blockType: String, professionId: String): Boolean {
        return when (professionId) {
            "miner" -> isOreBlock(blockType)
            "lumberjack" -> isLogBlock(blockType)
            "farmer" -> isFarmerCropBlock(blockType)
            else -> false
        }
    }

    private fun isFarmerCropBlock(blockType: String): Boolean {
        return blockType in FARMER_TARGET_BLOCKS
    }

    private fun isFarmerHarvestItem(itemType: Material): Boolean {
        return itemType in FARMER_HARVEST_ITEMS
    }

    private fun resolveBlockType(event: org.bukkit.event.block.BlockDropItemEvent, playerUuid: UUID): String {
        val blockStateType = event.blockState.type.name
        if (blockStateType != "AIR") {
            return blockStateType
        }

        val eventBlockType = event.block.type.name
        if (eventBlockType != "AIR") {
            return eventBlockType
        }

        return BlockBreakEffectListener.getCachedBlockTypeForPlayer(playerUuid) ?: "AIR"
    }

    /**
     * 鉱石系ブロック判定
     * - *_ORE で終わるもの
     * - ANCIENT_DEBRIS（ネザー鉱石）
     * - NETHER_GOLD_ORE
     */
    private fun isOreBlock(blockType: String): Boolean {
        return blockType.endsWith("_ORE") || 
               blockType == "ANCIENT_DEBRIS" || 
               blockType == "NETHER_GOLD_ORE"
    }

    /**
     * 木系ブロック判定
     * - *_LOG で終わるもの
     * - *_WOOD で終わるもの
     * - *_STEM で終わるもの
     * - *_HYPHAE で終わるもの
     */
    private fun isLogBlock(blockType: String): Boolean {
        return blockType.endsWith("_LOG") ||
               blockType.endsWith("_WOOD") ||
               blockType.endsWith("_STEM") ||
               blockType.endsWith("_HYPHAE")
    }

    /**
     * Minecraft の Fortune エンチャント計算に準拠した、ドロップ数計算
     * 参考: https://minecraft.wiki/wiki/Fortune
     *
     * Fortune なし (0): drop_amount = base
     * Fortune I (1):   drop_amount = base + random(0, 1)
     * Fortune II (2):  drop_amount = base + random(0, 2)
     * Fortune III (3): drop_amount = base + random(0, 3)
     *
     * @param baseAmount 基本ドロップ数
     * @param fortuneLevel 総幸運レベル（小数可）
     * @return 最終ドロップ数
     */
    private fun calculateFortuneDrops(baseAmount: Int, fortuneLevel: Double): Int {
        if (fortuneLevel <= 0) return baseAmount

        val bonusDrops = floor(Random.nextDouble(fortuneLevel + 1.0)).toInt()
        return baseAmount + bonusDrops
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        return skillEffect.getDoubleParam("fortune_level", 0.0)
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId in listOf("lumberjack", "miner", "farmer")
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val fortuneLevel = skillEffect.getDoubleParam("fortune_level", 0.0)
        return fortuneLevel > 0
    }
}
