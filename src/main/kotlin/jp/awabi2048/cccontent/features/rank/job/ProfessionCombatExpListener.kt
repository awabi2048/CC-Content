package jp.awabi2048.cccontent.features.rank.job

import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.skill.ToolType
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Enemy
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import kotlin.math.ceil

class ProfessionCombatExpListener(
    private val rankManager: RankManager,
    private val config: FileConfiguration
) : Listener {

    companion object {
        private const val EXP_MULTIPLIER_PATH = "rank.combat_exp.health_multiplier"
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        val killer = event.entity.killer ?: return
        val entity = event.entity

        if (entity !is Enemy) {
            return
        }

        val expMultiplier = config.getDouble(EXP_MULTIPLIER_PATH, 0.1)
        if (expMultiplier <= 0.0) {
            return
        }

        val playerProfession = rankManager.getPlayerProfession(killer.uniqueId) ?: return
        val requiredTool = when (playerProfession.profession) {
            Profession.SWORDSMAN -> ToolType.SWORD
            Profession.WARRIOR -> ToolType.AXE
            else -> return
        }

        val heldTool = ToolType.fromMaterial(killer.inventory.itemInMainHand.type)
        if (heldTool != requiredTool) {
            return
        }

        val maxHealth = entity.getAttribute(Attribute.MAX_HEALTH)?.value ?: entity.health
        val expAmount = ceil(maxHealth * expMultiplier).toLong().coerceAtLeast(1L)
        rankManager.addProfessionExp(killer.uniqueId, expAmount)
    }
}
