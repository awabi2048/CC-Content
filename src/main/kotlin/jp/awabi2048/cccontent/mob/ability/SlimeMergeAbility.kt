package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.ability.MobAbilityRuntime
import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import jp.awabi2048.cccontent.mob.MobSpawnOptions
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import kotlin.random.Random

class SlimeMergeAbility(
    override val id: String,
    private val selfDefinitionId: String,
    private val mergeOrder: List<String>,
    private val mergeRadius: Double = 3.0,
    private val cooldownTicks: Long = 60L,
    private val initialCooldownTicks: Long = 40L
) : MobAbility {

    data class Runtime(
        var cooldownTicks: Long
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(cooldownTicks = initialCooldownTicks)
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return

        if (rt.cooldownTicks > 0L) {
            rt.cooldownTicks -= 10L
            return
        }

        if (!context.isCombatActive()) return

        val selfIndex = mergeOrder.indexOf(selfDefinitionId)
        if (selfIndex < 0 || selfIndex >= mergeOrder.lastIndex) return

        if (!MobAbilityUtils.shouldProcessSearchTick(context.activeMob.tickCount, context.loadSnapshot.searchIntervalMultiplier, Random.nextInt(SEARCH_PHASE_VARIANTS))) return
        if (Random.nextDouble() < context.loadSnapshot.abilityExecutionSkipChance) return

        val entity = context.entity
        val mobService = MobService.getInstance(context.plugin) ?: return

        val nearby = entity.getNearbyEntities(mergeRadius, mergeRadius, mergeRadius)
        for (other in nearby) {
            if (other === entity) continue
            val otherActive = mobService.getActiveMob(other.uniqueId) ?: continue
            if (otherActive.sessionKey != context.sessionKey) continue

            val partnerIndex = mergeOrder.indexOf(otherActive.definition.id)
            if (partnerIndex != selfIndex || partnerIndex >= mergeOrder.lastIndex) continue

            val resultIndex = minOf(mergeOrder.lastIndex, maxOf(selfIndex, partnerIndex) + 1)
            val resultDefinitionId = mergeOrder[resultIndex]

            val midX = (entity.location.x + other.location.x) / 2.0
            val midY = (entity.location.y + other.location.y) / 2.0
            val midZ = (entity.location.z + other.location.z) / 2.0
            val spawnLocation = Location(entity.world, midX, midY, midZ)

            val options = MobSpawnOptions(
                featureId = context.activeMob.featureId,
                sessionKey = context.sessionKey,
                combatActiveProvider = context.activeMob.combatActiveProvider
            )

            val selfId = entity.uniqueId
            val partnerId = other.uniqueId

            Bukkit.getScheduler().runTask(context.plugin, Runnable {
                if (!entity.isValid || entity.isDead) return@Runnable
                if (!other.isValid || other.isDead) return@Runnable

                mobService.untrack(partnerId)
                other.remove()

                mobService.untrack(selfId)
                entity.remove()

                playTransitionEffect(spawnLocation)

                mobService.spawnByDefinitionId(resultDefinitionId, spawnLocation, options)
            })

            rt.cooldownTicks = cooldownTicks
            return
        }
    }

    override fun onDeath(context: MobDeathContext, runtime: MobAbilityRuntime?) {
        val selfIndex = mergeOrder.indexOf(selfDefinitionId)
        if (selfIndex <= 0) return

        val resultDefinitionId = mergeOrder[selfIndex - 1]
        val mobService = MobService.getInstance(context.plugin) ?: return
        val spawnLocation = context.entity.location.clone().add(
            Random.nextDouble(-0.3, 0.3),
            0.1,
            Random.nextDouble(-0.3, 0.3)
        )

        val options = MobSpawnOptions(
            featureId = context.activeMob.featureId,
            sessionKey = context.sessionKey,
            combatActiveProvider = context.activeMob.combatActiveProvider,
            metadata = context.activeMob.metadata + ("slime_downgrade" to "true")
        )

        val downgraded = mobService.spawnByDefinitionId(resultDefinitionId, spawnLocation, options) ?: return
        playTransitionEffect(downgraded.location)
    }

    private fun playTransitionEffect(location: Location) {
        location.world?.playSound(location, Sound.ENTITY_SLIME_SQUISH, 1.2f, 0.5f)
        location.world?.spawnParticle(Particle.ITEM_SLIME, location, 20, 0.5, 0.5, 0.5, 0.1)
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
    }
}
