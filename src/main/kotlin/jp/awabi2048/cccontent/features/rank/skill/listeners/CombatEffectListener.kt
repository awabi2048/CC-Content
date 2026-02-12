package jp.awabi2048.cccontent.features.rank.skill.listeners

import jp.awabi2048.cccontent.features.rank.skill.SkillEffectEngine
import jp.awabi2048.cccontent.features.rank.skill.handlers.AttackReachBoostHandler
import jp.awabi2048.cccontent.features.rank.skill.handlers.CombatDamageBoostHandler
import jp.awabi2048.cccontent.features.rank.skill.handlers.SweepAttackDamageBoostHandler
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
 * 以下の3つのスキル効果を統括して管理する:
 * - combat.profession_damage_boost : 職業ツールによる攻撃ダメージ強化
 * - combat.sweep_damage_boost      : スウィープ攻撃ダメージ強化
 * - combat.attack_reach_boost      : 攻撃リーチ拡張
 */
class CombatEffectListener : Listener {

    // ─── 攻撃ダメージイベント ───────────────────────────────────────────────

    /**
     * プレイヤーがエンティティを攻撃した際に呼ばれる。
     * 職業ツールによる攻撃かどうかを判定し、スキル効果を適用する。
     *
     * - combat.profession_damage_boost: 攻撃力モディファイアは常時更新済みのため直接判定のみ
     * - combat.sweep_damage_boost     : スウィープ攻撃時にダメージを乗算
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val playerUuid = attacker.uniqueId
        val compiledEffects = SkillEffectEngine.getCachedEffects(playerUuid) ?: return
        val profession = compiledEffects.profession

        // ─── 職業ツールによる攻撃ダメージ強化 ──────────────────────────────
        val damageEntries = compiledEffects.byType[CombatDamageBoostHandler.EFFECT_TYPE]
        if (!damageEntries.isNullOrEmpty()) {
            val heldItem = attacker.inventory.itemInMainHand.type.name

            // 有効なスキルエフェクトを対象ウェポンでフィルタリングし、乗算係数を計算
            val totalMultiplier = damageEntries
                .filter { entry ->
                    val targetWeapons = entry.effect.getStringListParam("targetWeapons")
                    targetWeapons.isEmpty() || targetWeapons.any { heldItem.contains(it) }
                }
                .fold(1.0) { acc, entry ->
                    acc * entry.effect.getDoubleParam("multiplier", 1.0)
                }

            if (totalMultiplier > 1.0) {
                // MULTIPLY_SCALAR_1 モディファイアは既に適用済みなので、
                // ここでは追加でイベントダメージを直接変更しない。
                // （常時適用型のモディファイアで処理）
                CombatDamageBoostHandler.applyModifier(playerUuid, totalMultiplier)
            }
        }

        // ─── スウィープ攻撃ダメージ強化 ──────────────────────────────────
        if (event.cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            val sweepEntries = compiledEffects.byType[SweepAttackDamageBoostHandler.EFFECT_TYPE]
            if (!sweepEntries.isNullOrEmpty()) {
                // 複数スキルの sweep_multiplier を乗算
                val totalSweepMultiplier = sweepEntries.fold(1.0) { acc, entry ->
                    acc * entry.effect.getDoubleParam("sweep_multiplier", 1.0)
                }
                if (totalSweepMultiplier > 1.0) {
                    event.damage = event.damage * totalSweepMultiplier
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
        CombatDamageBoostHandler.removeModifier(playerUuid)
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
        // targetWeapons は最後のエフェクトのものを使用（通常は1スキル想定）
        val targetWeapons = reachEntries.last().effect.getStringListParam("targetWeapons")

        AttackReachBoostHandler.reevaluateReach(playerUuid, targetWeapons, totalReach)
    }
}
