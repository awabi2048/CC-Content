package jp.awabi2048.cccontent.features.arena

import com.awabi2048.ccsystem.api.event.PlayerLeftClickPlayerEvent
import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.mob.ability.MobShootUtil
import jp.awabi2048.cccontent.mob.event.CustomEntityMobSpawnEvent
import jp.awabi2048.cccontent.mob.event.CustomMobSpawnEvent
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.EnderPearl
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.entity.EntityPotionEffectEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.entity.EntityToggleGlideEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.EntityMountEvent
import org.bukkit.event.entity.EntityRemoveEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.persistence.PersistentDataType
import kotlin.random.Random

class ArenaListener(private val arenaManager: ArenaManager) : Listener {

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        arenaManager.handleMobDeath(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityRemove(event: EntityRemoveEvent) {
        if (event.cause != EntityRemoveEvent.Cause.DEATH) {
            return
        }
        arenaManager.handleEntityDeath(event.entity)
    }

    @EventHandler
    fun onCustomMobSpawn(event: CustomMobSpawnEvent) {
        arenaManager.registerChildMob(event.entity, event.definition.typeId, event.options)
    }

    @EventHandler
    fun onCustomEntityMobSpawn(event: CustomEntityMobSpawnEvent) {
        arenaManager.registerChildEntityMob(event.entity, event.definition.typeId, event.options)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        arenaManager.handleParticipantDamage(event)
        if (event.isCancelled) {
            return
        }
        arenaManager.handleMobFallDamage(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        arenaManager.handleParticipantFriendlyFire(event)
        if (event.isCancelled) {
            return
        }
        arenaManager.handleDownedPlayerAttack(event)
        if (event.isCancelled) {
            return
        }
        arenaManager.handleMobFriendlyFire(event)
        if (event.isCancelled) {
            return
        }
        arenaManager.handleMobDamagedByParticipant(event)
        handleConfusionArrowHit(event)
        handleConfusionMeleeMiss(event)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onShootBow(event: EntityShootBowEvent) {
        handleConfusionBowNoise(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onProjectileLaunch(event: ProjectileLaunchEvent) {
        val pearl = event.entity as? EnderPearl ?: return
        val player = pearl.shooter as? Player ?: return
        if (arenaManager.getSession(player) != null) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityToggleGlide(event: EntityToggleGlideEvent) {
        if (!event.isGliding) return
        val player = event.entity as? Player ?: return
        if (arenaManager.cancelArenaGlide(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityPotionEffect(event: EntityPotionEffectEvent) {
        arenaManager.handleElderGuardianCurse(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerLeftClickPlayer(event: PlayerLeftClickPlayerEvent) {
        if (arenaManager.handlePlayerLeftClickPlayer(event.player, event.target)) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityTarget(event: EntityTargetLivingEntityEvent) {
        arenaManager.handleMobTarget(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        arenaManager.handleArenaBlockBreak(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        arenaManager.handleArenaBlockPlace(event)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        arenaManager.handleArenaInteract(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        if (event.cause != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            return
        }
        if (arenaManager.getSession(event.player) != null) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        arenaManager.handleArenaInteractEntity(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityMount(event: EntityMountEvent) {
        arenaManager.handleArenaEntityMount(event)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        arenaManager.handleParticipantQuit(event.player)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        arenaManager.handleParticipantJoin(event.player)
    }

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        arenaManager.clearLobbyTutorialState(event.player)
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (arenaManager.getSession(event.player) == null) return
        event.deathMessage = null
        arenaManager.notifyParticipantDeath(event.player)
        arenaManager.handleInviteTargetUnavailable(event.player)
        arenaManager.stopSession(
            event.player,
            ArenaI18n.text(event.player, "arena.messages.session.ended_by_death")
        )
    }

    private fun handleConfusionMeleeMiss(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        val session = arenaManager.getSession(player) ?: return
        if (session.worldName != player.world.name) return
        if (!arenaManager.confusionManager.isConfused(player.uniqueId)) return
        if (event.entity !is org.bukkit.entity.LivingEntity) return
        val damager = event.damager
        if (damager is Player && damager.uniqueId == player.uniqueId) {
            if (Random.nextDouble() < CONFUSION_MELEE_MISS_CHANCE) {
                event.isCancelled = true
                player.world.playSound(player.location, Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1.0f, 1.0f)
            }
        }
    }

    private fun handleConfusionArrowHit(event: EntityDamageByEntityEvent) {
        val player = event.entity as? Player ?: return
        val session = arenaManager.getSession(player) ?: return
        if (session.worldName != player.world.name) return

        val arrow = event.damager as? AbstractArrow ?: return
        val container = arrow.persistentDataContainer
        if (container.get(confusionArrowFlagKey(), PersistentDataType.BYTE)?.toInt() != 1) {
            return
        }

        val effectTypeName = container.get(confusionArrowTypeKey(), PersistentDataType.STRING) ?: return
        if (effectTypeName != MobShootUtil.CONFUSION_EFFECT_NAME) {
            return
        }

        val durationTicks = (container.get(confusionArrowAmpKey(), PersistentDataType.INTEGER) ?: 200).coerceAtLeast(1)
        arenaManager.confusionManager.applyConfusion(player.uniqueId, durationTicks.toLong())
    }

    private fun handleConfusionBowNoise(event: EntityShootBowEvent) {
        val player = event.entity as? Player ?: return
        val session = arenaManager.getSession(player) ?: return
        if (session.worldName != player.world.name) return
        if (!arenaManager.confusionManager.isConfused(player.uniqueId)) return
        val arrow = event.projectile as? AbstractArrow ?: return
        if (Random.nextDouble() < CONFUSION_BOW_NOISE_CHANCE) {
            val noiseX = Random.nextDouble(-CONFUSION_BOW_NOISE_RANGE, CONFUSION_BOW_NOISE_RANGE)
            val noiseY = Random.nextDouble(-CONFUSION_BOW_NOISE_RANGE_Y, CONFUSION_BOW_NOISE_RANGE_Y)
            val noiseZ = Random.nextDouble(-CONFUSION_BOW_NOISE_RANGE, CONFUSION_BOW_NOISE_RANGE)
            arrow.velocity = arrow.velocity.add(org.bukkit.util.Vector(noiseX, noiseY, noiseZ))
        }
    }

    private companion object {
        private const val CONFUSION_MELEE_MISS_CHANCE = 0.5
        private const val CONFUSION_BOW_NOISE_CHANCE = 0.5
        private const val CONFUSION_BOW_NOISE_RANGE = 0.4
        private const val CONFUSION_BOW_NOISE_RANGE_Y = 0.1

        private fun confusionArrowFlagKey(): NamespacedKey = NamespacedKey(CCContent.instance, "skeleton_effect_arrow")
        private fun confusionArrowTypeKey(): NamespacedKey = NamespacedKey(CCContent.instance, "skeleton_effect_arrow_type")
        private fun confusionArrowAmpKey(): NamespacedKey = NamespacedKey(CCContent.instance, "skeleton_effect_arrow_amp")
    }
}
