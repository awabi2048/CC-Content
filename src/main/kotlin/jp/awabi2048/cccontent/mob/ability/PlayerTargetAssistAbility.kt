package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.GameMode
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player

class PlayerTargetAssistAbility(
    override val id: String,
    private val searchRadius: Double = 18.0,
    private val chaseSpeed: Double = 1.15
) : MobAbility {

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        if (!context.isCombatActive()) return

        val mob = context.entity as? Mob ?: return
        val currentTarget = mob.target as? Player
        if (currentTarget != null && isChaseTargetValid(mob, currentTarget)) {
            mob.pathfinder.moveTo(currentTarget.location, chaseSpeed)
            return
        }

        val nextTarget = findNearestPlayer(mob, searchRadius) ?: return
        mob.target = nextTarget
        mob.pathfinder.moveTo(nextTarget.location, chaseSpeed)
    }

    private fun findNearestPlayer(mob: LivingEntity, radius: Double): Player? {
        return mob.getNearbyEntities(radius, radius, radius)
            .asSequence()
            .mapNotNull { it as? Player }
            .filter { isChaseTargetValid(mob, it) }
            .minByOrNull { it.location.distanceSquared(mob.location) }
    }

    private fun isChaseTargetValid(mob: LivingEntity, player: Player): Boolean {
        if (!player.isValid || player.isDead) return false
        if (player.gameMode == GameMode.SPECTATOR) return false
        if (player.world.uid != mob.world.uid) return false
        return true
    }
}
