package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.job.IgnoreBlockStore
import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectEngine
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID
import kotlin.math.floor
import kotlin.random.Random

class FarmerAreaHarvestingHandler(
    private val plugin: JavaPlugin,
    private val rankManager: RankManager,
    private val ignoreBlockStore: IgnoreBlockStore
) : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "farmer.area_harvesting"
        private const val DEFAULT_RADIUS = 2
        private const val MAX_RADIUS = 6
    }

    private val farmerExpMap: Map<Material, Long>

    init {
        val jobDir = File(plugin.dataFolder, "job").apply { mkdirs() }
        val expFile = File(jobDir, "exp.yml")
        val config = if (expFile.exists()) YamlConfiguration.loadConfiguration(expFile) else YamlConfiguration()
        farmerExpMap = loadBlockExpMap(config, "farmer")
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.RUNTIME

    override fun applyEffect(context: EffectContext): Boolean {
        val event = context.getEvent<BlockBreakEvent>() ?: return false
        val player = event.player
        if (!FarmerCropSupport.isHoe(player.inventory.itemInMainHand.type)) {
            return false
        }

        val center = event.block
        val centerType = center.type
        val centerData = center.blockData.clone()
        if (!FarmerCropSupport.isHarvestableCrop(centerType, centerData)) {
            return false
        }

        val radius = context.skillEffect.getIntParam("radius", DEFAULT_RADIUS).coerceIn(1, MAX_RADIUS)
        val targets = collectHarvestTargets(center, centerType, radius)
        if (targets.isEmpty()) {
            return false
        }

        val originTool = player.inventory.itemInMainHand.clone()
        val totalFortuneLevel = resolveTotalFortuneLevel(player.uniqueId, centerType.name, originTool)
        val autoReplantEnabled = SkillEffectEngine.hasCachedEffect(player.uniqueId, FarmerAutoReplantingHandler.EFFECT_TYPE)

        var totalExp = 0L
        var harvestedCount = 0

        for (target in targets) {
            val originalType = target.type
            val originalData = target.blockData.clone()

            val expGain = calculateExpGain(target, originalType, originalData)
            val boostedDrops = calculateBoostedDrops(target, originTool, player, totalFortuneLevel)

            target.type = Material.AIR
            dropStacks(target, boostedDrops)

            if (autoReplantEnabled) {
                FarmerCropSupport.tryConsumeAndReplant(player, target, originalType, originalData)
            }

            totalExp += expGain
            harvestedCount++
        }

        if (totalExp > 0L) {
            rankManager.addProfessionExp(player.uniqueId, totalExp)
        }

        return harvestedCount > 0
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        val radius = skillEffect.getIntParam("radius", DEFAULT_RADIUS).coerceIn(1, MAX_RADIUS)
        val oneSide = radius * 2 + 1
        return (oneSide * oneSide * oneSide).toDouble()
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId == "farmer"
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        return skillEffect.getIntParam("radius", DEFAULT_RADIUS) > 0
    }

    private fun collectHarvestTargets(center: Block, originType: Material, radius: Int): List<Block> {
        val targets = mutableListOf<Block>()
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                for (dz in -radius..radius) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue
                    }

                    val target = center.world.getBlockAt(center.x + dx, center.y + dy, center.z + dz)
                    if (target.type != originType) {
                        continue
                    }

                    if (!FarmerCropSupport.isHarvestableCrop(target.type, target.blockData)) {
                        continue
                    }

                    targets.add(target)
                }
            }
        }
        return targets
    }

    private fun calculateExpGain(target: Block, originalType: Material, originalData: org.bukkit.block.data.BlockData): Long {
        val expAmount = farmerExpMap[originalType] ?: return 0L
        if (expAmount <= 0L) {
            return 0L
        }

        if (!FarmerCropSupport.shouldGrantExp(target, originalType, originalData, ignoreBlockStore)) {
            return 0L
        }

        return expAmount
    }

    private fun calculateBoostedDrops(target: Block, tool: ItemStack, player: org.bukkit.entity.Player, totalFortuneLevel: Double): List<ItemStack> {
        val drops = target.getDrops(tool, player).map { it.clone() }
        if (totalFortuneLevel <= 0.0) {
            return drops
        }

        for (stack in drops) {
            if (!FarmerCropSupport.isFarmerHarvestItem(stack.type)) {
                continue
            }

            val increasedAmount = calculateFortuneDrops(stack.amount, totalFortuneLevel)
            stack.amount = increasedAmount
        }

        return drops
    }

    private fun resolveTotalFortuneLevel(playerUuid: UUID, blockType: String, tool: ItemStack): Double {
        val vanillaFortune = tool.getEnchantmentLevel(Enchantment.FORTUNE).toDouble()
        val entries = SkillEffectEngine.getCachedEffects(playerUuid)?.byType?.get("collect.drop_bonus") ?: emptyList()
        val combined = SkillEffectEngine.combineEffects(entries, blockType)
        val skillFortune = combined?.getDoubleParam("fortune_level", 0.0) ?: 0.0
        return vanillaFortune + skillFortune
    }

    private fun calculateFortuneDrops(baseAmount: Int, fortuneLevel: Double): Int {
        if (baseAmount <= 0) {
            return 0
        }

        val enchantmentBonus = floor(Random.nextDouble() * (fortuneLevel + 1.0)).toInt()
        val randomVariation = Random.nextInt(0, 2)
        return maxOf(baseAmount, baseAmount + enchantmentBonus + randomVariation)
    }

    private fun dropStacks(target: Block, drops: List<ItemStack>) {
        if (drops.isEmpty()) {
            return
        }

        val location = target.location.clone().add(0.5, 0.5, 0.5)
        for (stack in drops) {
            if (stack.type.isAir || stack.amount <= 0) {
                continue
            }
            target.world.dropItemNaturally(location, stack)
        }
    }

    private fun loadBlockExpMap(config: YamlConfiguration, path: String): Map<Material, Long> {
        val section = config.getConfigurationSection(path) ?: return emptyMap()
        val result = mutableMapOf<Material, Long>()

        for (blockKey in section.getKeys(false)) {
            val material = Material.matchMaterial(blockKey.uppercase()) ?: continue
            val exp = section.getLong(blockKey, 0L)
            if (exp > 0L) {
                result[material] = exp
            }
        }

        return result
    }
}
