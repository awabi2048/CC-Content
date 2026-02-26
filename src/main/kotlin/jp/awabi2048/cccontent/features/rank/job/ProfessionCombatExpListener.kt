package jp.awabi2048.cccontent.features.rank.job

import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.skill.ToolType
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Enemy
import org.bukkit.entity.EntityType
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import kotlin.math.ceil

class ProfessionCombatExpListener(
    private val rankManager: RankManager,
    private val config: FileConfiguration,
) : Listener {
    companion object {
        private const val EXP_MULTIPLIER_PATH = "rank.combat_exp.health_multiplier"
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        val killer = resolveKiller(event) ?: return
        val entity = event.entity

        if (!isHostileEntity(entity)) {
            return
        }

        val expMultiplier = config.getDouble(EXP_MULTIPLIER_PATH, 0.1)
        if (expMultiplier <= 0.0) {
            return
        }

        val playerProfession = rankManager.getPlayerProfession(killer.uniqueId) ?: return
        if (!isEligibleKillByProfession(event, killer, playerProfession.profession)) {
            return
        }

        val maxHealth = entity.getAttribute(Attribute.MAX_HEALTH)?.value ?: entity.health
        val expAmount = ceil(maxHealth * expMultiplier).toLong().coerceAtLeast(1L)

        rankManager.addProfessionExp(killer.uniqueId, expAmount)
    }

    private fun isEligibleKillByProfession(event: EntityDeathEvent, killer: Player, profession: Profession): Boolean {
        return when (profession) {
            Profession.SWORDSMAN -> isHoldingRequiredTool(killer, ToolType.SWORD)
            Profession.WARRIOR -> isHoldingRequiredTool(killer, ToolType.AXE) || isBowKill(event, killer)
            else -> false
        }
    }

    private fun isHoldingRequiredTool(player: Player, requiredTool: ToolType): Boolean {
        val heldTool = ToolType.fromMaterial(player.inventory.itemInMainHand.type)
        return heldTool == requiredTool
    }

    private fun isBowKill(event: EntityDeathEvent, killer: Player): Boolean {
        val damageEvent = event.entity.lastDamageCause as? EntityDamageByEntityEvent ?: return false
        val projectile = damageEvent.damager as? Projectile ?: return false
        val shooter = projectile.shooter as? Player ?: return false
        if (shooter.uniqueId != killer.uniqueId) {
            return false
        }
        return projectile.type == EntityType.ARROW || projectile.type == EntityType.SPECTRAL_ARROW
    }

    private fun resolveKiller(event: EntityDeathEvent): Player? {
        event.entity.killer?.let { return it }

        val damageEvent = event.entity.lastDamageCause as? EntityDamageByEntityEvent ?: return null
        return when (val damager = damageEvent.damager) {
            is Player -> damager
            is Projectile -> damager.shooter as? Player
            else -> null
        }
    }

    private fun isHostileEntity(entity: org.bukkit.entity.LivingEntity): Boolean {
        return entity is Enemy || entity is Monster
    }
}
