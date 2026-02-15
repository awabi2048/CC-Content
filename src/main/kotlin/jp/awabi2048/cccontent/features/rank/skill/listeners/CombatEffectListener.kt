package jp.awabi2048.cccontent.features.rank.skill.listeners

import jp.awabi2048.cccontent.features.rank.skill.SkillEffectEngine
import jp.awabi2048.cccontent.features.rank.skill.handlers.AttackReachBoostHandler
import jp.awabi2048.cccontent.features.rank.skill.handlers.CombatDamageBoostHandler
import jp.awabi2048.cccontent.features.rank.skill.handlers.SweepAttackDamageBoostHandler
import jp.awabi2048.cccontent.features.rank.skill.handlers.WarriorAxeDamageBoostHandler
import jp.awabi2048.cccontent.features.rank.profession.Profession
import org.bukkit.attribute.Attribute
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * 戦闘系スキル効果のイベントリスナー
 *
 * 以下のスキル効果を統括して管理する:
 * - swordsman.sword_damage_boost   : Sharpness追加レベルによる剣ダメージ強化
 * - warrior.axe_damage_boost       : Sharpness追加レベルによる斧ダメージ強化
 * - swordsman.sweep_damage_boost   : Sweeping Edge追加レベルによるスウィープ強化
 * - combat.attack_reach_boost      : 攻撃リーチ拡張
 */
class CombatEffectListener : Listener {

    // ─── 攻撃ダメージイベント ───────────────────────────────────────────────

    /**
     * プレイヤーがエンティティを攻撃した際に呼ばれる。
     * 職業ツールによる攻撃かどうかを判定し、スキル効果を適用する。
     *
     * - swordsman.sword_damage_boost  : Sharpness追加レベル分の差分ダメージを加算
     * - warrior.axe_damage_boost      : Sharpness追加レベル分の差分ダメージを加算
     * - swordsman.sweep_damage_boost  : Sweeping Edge追加レベル分の差分ダメージを加算
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val compiledEffects = SkillEffectEngine.getCachedEffects(attacker.uniqueId) ?: return
        val profession = compiledEffects.profession
        val mainHand = attacker.inventory.itemInMainHand
        val heldItemTypeName = mainHand.type.name

        // ─── 職業ツールによる攻撃ダメージ強化 ──────────────────────────────
        when (profession) {
            Profession.SWORDSMAN -> {
                if (heldItemTypeName.contains("SWORD")) {
                    val bonusSharpness = compiledEffects.byType[CombatDamageBoostHandler.EFFECT_TYPE]
                        ?.sumOf { entry ->
                            entry.effect.getDoubleParam("sharpness", 0.0).coerceAtLeast(0.0)
                        }
                        ?: 0.0

                    if (bonusSharpness > 0.0) {
                        val actualSharpness = mainHand.getEnchantmentLevel(Enchantment.SHARPNESS).toDouble().coerceAtLeast(0.0)
                        val effectiveSharpness = actualSharpness + bonusSharpness
                        val deltaDamage = sharpnessBonusDamage(effectiveSharpness) - sharpnessBonusDamage(actualSharpness)
                        if (deltaDamage > 0.0) {
                            event.damage += deltaDamage
                        }
                    }
                }
            }
            Profession.WARRIOR -> {
                if (heldItemTypeName.contains("AXE")) {
                    val bonusSharpness = compiledEffects.byType[WarriorAxeDamageBoostHandler.EFFECT_TYPE]
                        ?.sumOf { entry ->
                            entry.effect.getDoubleParam("sharpness", 0.0).coerceAtLeast(0.0)
                        }
                        ?: 0.0

                    if (bonusSharpness > 0.0) {
                        val actualSharpness = mainHand.getEnchantmentLevel(Enchantment.SHARPNESS).toDouble().coerceAtLeast(0.0)
                        val effectiveSharpness = actualSharpness + bonusSharpness
                        val deltaDamage = sharpnessBonusDamage(effectiveSharpness) - sharpnessBonusDamage(actualSharpness)
                        if (deltaDamage > 0.0) {
                            event.damage += deltaDamage
                        }
                    }
                }
            }
            else -> {}
        }

        // ─── スウィープ攻撃ダメージ強化 ──────────────────────────────────
        if (profession == Profession.SWORDSMAN && event.cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK && heldItemTypeName.contains("SWORD")) {
            val sweepEntries = compiledEffects.byType[SweepAttackDamageBoostHandler.EFFECT_TYPE]
            if (!sweepEntries.isNullOrEmpty()) {
                val bonusSweepingEdge = sweepEntries
                    .sumOf { entry ->
                        entry.effect.getDoubleParam("sweeping_edge", 0.0).coerceAtLeast(0.0)
                    }

                if (bonusSweepingEdge > 0.0) {
                    val actualSweepingEdge = mainHand.getEnchantmentLevel(Enchantment.SWEEPING_EDGE).toDouble().coerceAtLeast(0.0)
                    val effectiveSweepingEdge = actualSweepingEdge + bonusSweepingEdge
                    val deltaRatio = sweepingEdgeRatio(effectiveSweepingEdge) - sweepingEdgeRatio(actualSweepingEdge)
                    val attackDamage = attacker.getAttribute(Attribute.ATTACK_DAMAGE)?.value ?: 0.0
                    val deltaDamage = attackDamage * deltaRatio
                    if (deltaDamage > 0.0) {
                        event.damage += deltaDamage
                    }
                }
            }
        }
    }

    // ─── アイテム持ち替えイベント ───────────────────────────────────────────

    /**
     * プレイヤーがホットバーのスロットを切り替えた際に呼ばれる。
     * 職業ツールを手に持っているかどうかを再評価し、攻撃リーチを更新する。
     */
    @EventHandler
    fun onItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        val playerUuid = player.uniqueId
        val compiledEffects = SkillEffectEngine.getCachedEffects(playerUuid) ?: run {
            AttackReachBoostHandler.removeModifier(playerUuid)
            return
        }

        reevaluateReach(playerUuid, compiledEffects)
    }

    /**
     * プレイヤーがログインした際に呼ばれる。
     * キャッシュが存在すれば攻撃リーチを適用する。
     */
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val playerUuid = event.player.uniqueId
        val compiledEffects = SkillEffectEngine.getCachedEffects(playerUuid) ?: return
        reevaluateReach(playerUuid, compiledEffects)
    }

    /**
     * プレイヤーがログアウトした際に呼ばれる。
     * Attribute Modifier を確実に除去する。
     */
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val playerUuid = event.player.uniqueId
        AttackReachBoostHandler.removeModifier(playerUuid)
    }

    // ─── ユーティリティ ────────────────────────────────────────────────────

    /**
     * プレイヤーの攻撃リーチを再評価して適用・除去する。
     * 複数スキルエフェクトがある場合は合計値（加算）を使用する。
     */
    private fun reevaluateReach(
        playerUuid: java.util.UUID,
        compiledEffects: jp.awabi2048.cccontent.features.rank.skill.CompiledEffects
    ) {
        val reachEntries = compiledEffects.byType[AttackReachBoostHandler.EFFECT_TYPE]
        if (reachEntries.isNullOrEmpty()) {
            AttackReachBoostHandler.removeModifier(playerUuid)
            return
        }

        // 全スキルの reach_blocks を合算
        val totalReach = reachEntries.sumOf { it.effect.getDoubleParam("reach_blocks", 0.0) }
        val targetWeapons = getHardcodedReachWeapons(compiledEffects.profession)

        AttackReachBoostHandler.reevaluateReach(playerUuid, targetWeapons, totalReach)
    }

    private fun sharpnessBonusDamage(level: Double): Double {
        if (level <= 0) {
            return 0.0
        }
        return level * 0.5 + 0.5
    }

    private fun sweepingEdgeRatio(level: Double): Double {
        if (level <= 0) {
            return 0.0
        }
        return level / (level + 1.0)
    }

    private fun getHardcodedReachWeapons(profession: Profession): List<String> {
        return when (profession) {
            Profession.SWORDSMAN -> listOf("SWORD")
            Profession.WARRIOR -> listOf("AXE")
            else -> emptyList()
        }
    }
}
