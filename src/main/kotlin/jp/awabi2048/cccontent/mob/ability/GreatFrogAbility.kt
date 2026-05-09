@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.mob.ability

import com.destroystokyo.paper.entity.ai.Goal
import com.destroystokyo.paper.entity.ai.GoalKey
import com.destroystokyo.paper.entity.ai.GoalType
import jp.awabi2048.cccontent.mob.MobAttackContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobSpawnContext
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Frog
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Slime
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import java.util.EnumSet
import java.util.UUID

class GreatFrogAbility(
    override val id: String,
    private val searchRadius: Double = 24.0,
    private val tongueMinRange: Double = 1.5,
    private val tongueMaxRange: Double = 10.0,
    private val tongueCooldownTicks: Long = 60L,
    private val tongueDamageMultiplier: Double = 1.0,
    private val tonguePullStrength: Double = 1.15,
    private val tongueSlownessDurationTicks: Int = 80,
    private val tongueSlownessAmplifier: Int = 1,
    private val tongueHitDelayTicks: Long = 12L,
    private val effectApplicationDurationTicks: Int = 100,
    private val auraRadius: Double = 4.5,
    private val auraIntervalTicks: Long = 20L,
    private val slimeSearchRadius: Double = 6.0,
    private val slimeEatCooldownTicks: Long = 30L * 20L
) : MobAbility {

    data class StoredEffect(
        val effectType: PotionEffectType,
        val amplifier: Int
    )

    data class Runtime(
        var tongueCooldownTicks: Long = 0L,
        var slimeEatCooldownTicks: Long = 0L,
        var storedEffect: StoredEffect? = null
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: MobSpawnContext): MobAbilityRuntime {
        return Runtime()
    }

    override fun onSpawn(context: MobSpawnContext, runtime: MobAbilityRuntime?) {
        val frog = context.entity as? Frog ?: return
        val mobGoals = Bukkit.getMobGoals()

        mobGoals.removeAllGoals(frog)

        val rt = runtime as? Runtime ?: return
        mobGoals.addGoal(frog, 2, GreatFrogChaseGoal(frog, rt, this, context.plugin))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val frog = context.entity as? Frog ?: return

        if (rt.slimeEatCooldownTicks > 0L) {
            rt.slimeEatCooldownTicks = (rt.slimeEatCooldownTicks - context.tickDelta).coerceAtLeast(0L)
        }

        if (!context.isCombatActive()) return

        if (context.activeMob.tickCount % auraIntervalTicks == 0L) {
            applyStoredEffectToNearbyPlayers(frog, rt)
        }
    }

    override fun onAttack(context: MobAttackContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val target = context.target ?: return

        val storedEffect = rt.storedEffect
        if (storedEffect != null) {
            target.addPotionEffect(
                PotionEffect(
                    storedEffect.effectType,
                    effectApplicationDurationTicks,
                    storedEffect.amplifier,
                    false,
                    true,
                    true
                ),
                true
            )
        }

        target.addPotionEffect(
            PotionEffect(
                PotionEffectType.SLOWNESS,
                tongueSlownessDurationTicks,
                tongueSlownessAmplifier,
                false,
                true,
                true
            ),
            true
        )

        val pullDirection = context.entity.location.toVector().subtract(target.location.toVector())
        if (pullDirection.lengthSquared() >= 0.0001) {
            target.velocity = pullDirection.normalize().multiply(tonguePullStrength).setY(0.28)
        }
        target.world.playSound(target.location, Sound.ENTITY_SLIME_ATTACK, 0.8f, 0.9f)
    }

    private fun applyStoredEffectToNearbyPlayers(frog: Frog, runtime: Runtime) {
        val storedEffect = runtime.storedEffect ?: return

        frog.getNearbyEntities(auraRadius, auraRadius, auraRadius)
            .asSequence()
            .mapNotNull { it as? Player }
            .filter { isValidPlayerTarget(frog, it) }
            .forEach { player ->
                player.addPotionEffect(
                    PotionEffect(
                        storedEffect.effectType,
                        effectApplicationDurationTicks,
                        storedEffect.amplifier,
                        false,
                        true,
                        true
                    ),
                    true
                )
            }
    }

    private class GreatFrogChaseGoal(
        private val frog: Frog,
        private val runtime: Runtime,
        private val ability: GreatFrogAbility,
        private val plugin: Plugin
    ) : Goal<Frog> {

        private var pendingTongueTask: org.bukkit.scheduler.BukkitTask? = null

        override fun shouldActivate(): Boolean {
            return findSlimeTarget() != null || findPlayerTarget() != null
        }

        override fun shouldStayActive(): Boolean {
            return shouldActivate()
        }

        override fun stop() {
            pendingTongueTask?.cancel()
            pendingTongueTask = null
            frog.target = null
            frog.setTongueTarget(null)
        }

        override fun tick() {
            if (runtime.tongueCooldownTicks > 0L) {
                runtime.tongueCooldownTicks -= 1L
            }
            if (runtime.slimeEatCooldownTicks > 0L) {
                runtime.slimeEatCooldownTicks -= 1L
            }

            pendingTongueTask?.let { task ->
                if (!task.isCancelled) return
                pendingTongueTask = null
            }

            val slimeTarget = findSlimeTarget()
            val target: LivingEntity? = slimeTarget ?: findPlayerTarget()
            if (target == null) {
                frog.target = null
                return
            }

            if (target is Player) {
                frog.target = target
            }

            frog.pathfinder.moveTo(target.location, 1.15)

            if (runtime.tongueCooldownTicks > 0L) return

            val distance = frog.location.distance(target.location)
            if (distance < ability.tongueMinRange || distance > ability.tongueMaxRange) return
            if (!frog.hasLineOfSight(target)) return

            frog.setTongueTarget(target)
            runtime.tongueCooldownTicks = ability.tongueCooldownTicks

            val targetId = target.uniqueId
            val isSlime = slimeTarget != null
            pendingTongueTask = object : BukkitRunnable() {
                override fun run() {
                    pendingTongueTask = null

                    val entity = Bukkit.getEntity(targetId) ?: return

                    if (isSlime) {
                        val slime = entity as? Slime ?: return
                        if (!slime.isValid || slime.isDead) return

                        val effect = slime.activePotionEffects
                            .maxWithOrNull(compareBy<PotionEffect> { it.amplifier }.thenByDescending { it.duration })
                            ?: return

                        runtime.storedEffect = StoredEffect(effect.type, effect.amplifier)
                        runtime.slimeEatCooldownTicks = ability.slimeEatCooldownTicks

                        slime.world.spawnParticle(
                            Particle.ITEM_SLIME,
                            slime.location.clone().add(0.0, 0.5, 0.0),
                            20, 0.35, 0.25, 0.35, 0.08
                        )
                        slime.remove()
                    } else {
                        val player = entity as? Player ?: return
                        if (!player.isValid || player.isDead) return

                        val damage = ability.tongueDamageMultiplier.coerceAtLeast(0.0)
                        if (damage > 0.0) {
                            player.damage(damage, frog)
                        }

                        player.addPotionEffect(
                            PotionEffect(
                                PotionEffectType.SLOWNESS,
                                ability.tongueSlownessDurationTicks,
                                ability.tongueSlownessAmplifier,
                                false, true, true
                            ),
                            true
                        )

                        val storedEffect = runtime.storedEffect
                        if (storedEffect != null) {
                            player.addPotionEffect(
                                PotionEffect(
                                    storedEffect.effectType,
                                    ability.effectApplicationDurationTicks,
                                    storedEffect.amplifier,
                                    false, true, true
                                ),
                                true
                            )
                        }

                        val pullDirection = frog.location.toVector().subtract(player.location.toVector())
                        if (pullDirection.lengthSquared() >= 0.0001) {
                            player.velocity = pullDirection.normalize().multiply(ability.tonguePullStrength).setY(0.28)
                        }
                        player.world.playSound(player.location, Sound.ENTITY_SLIME_ATTACK, 0.8f, 0.9f)
                    }
                }
            }.runTaskLater(plugin, ability.tongueHitDelayTicks)
        }

        override fun getKey(): GoalKey<Frog> {
            return GOAL_KEY
        }

        override fun getTypes(): EnumSet<GoalType> {
            return EnumSet.of(GoalType.MOVE, GoalType.LOOK)
        }

        private fun findSlimeTarget(): Slime? {
            if (runtime.slimeEatCooldownTicks > 0L) return null

            return frog.getNearbyEntities(ability.searchRadius, ability.searchRadius, ability.searchRadius)
                .asSequence()
                .mapNotNull { it as? Slime }
                .filter { it.isValid && !it.isDead && it.world.uid == frog.world.uid }
                .filter { it.activePotionEffects.isNotEmpty() }
                .minByOrNull { it.location.distanceSquared(frog.location) }
        }

        private fun findPlayerTarget(): Player? {
            return frog.getNearbyEntities(ability.searchRadius, ability.searchRadius, ability.searchRadius)
                .asSequence()
                .mapNotNull { it as? Player }
                .filter { isValidPlayerTarget(frog, it) }
                .minByOrNull { it.location.distanceSquared(frog.location) }
        }

        companion object {
            private val GOAL_KEY = GoalKey.of(
                Frog::class.java,
                NamespacedKey("cc_content", "great_frog_chase")
            )
        }
    }

    companion object {
        fun isValidPlayerTarget(frog: LivingEntity, player: Player?): Boolean {
            if (player == null) return false
            if (!player.isValid || player.isDead) return false
            if (player.gameMode == GameMode.SPECTATOR) return false
            if (player.world.uid != frog.world.uid) return false
            return true
        }
    }
}
