package jp.awabi2048.cccontent.features.rank.skill.listeners

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectEngine
import jp.awabi2048.cccontent.features.rank.skill.handlers.WarriorBowPowerBoostHandler
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class WarriorBowEffectListener : Listener {

    companion object {
        private val plugin by lazy { JavaPlugin.getPlugin(CCContent::class.java) }
        private val bowPowerDeltaKey by lazy { NamespacedKey(plugin, "warrior_bow_power_delta") }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onShootBow(event: EntityShootBowEvent) {
        val shooter = event.entity as? Player ?: return
        val arrow = event.projectile as? AbstractArrow ?: return
        val bow = event.bow ?: return

        val compiledEffects = SkillEffectEngine.getCachedEffects(shooter.uniqueId) ?: return
        if (compiledEffects.profession != Profession.WARRIOR) {
            return
        }
        if (!bow.type.name.contains("BOW")) {
            return
        }

        val bonusPower = compiledEffects.byType[WarriorBowPowerBoostHandler.EFFECT_TYPE]
            ?.sumOf { it.effect.getDoubleParam("power", 0.0).coerceAtLeast(0.0) }
            ?: 0.0
        if (bonusPower <= 0.0) {
            return
        }

        val actualPower = bow.getEnchantmentLevel(Enchantment.POWER).toDouble().coerceAtLeast(0.0)
        val effectivePower = actualPower + bonusPower
        val deltaDamage = enchantLevelBonusDamage(effectivePower) - enchantLevelBonusDamage(actualPower)
        if (deltaDamage <= 0.0) {
            return
        }

        arrow.persistentDataContainer.set(bowPowerDeltaKey, PersistentDataType.DOUBLE, deltaDamage)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onArrowDamage(event: EntityDamageByEntityEvent) {
        val arrow = event.damager as? AbstractArrow ?: return
        val deltaDamage = arrow.persistentDataContainer.get(bowPowerDeltaKey, PersistentDataType.DOUBLE) ?: return
        if (deltaDamage <= 0.0) {
            return
        }
        event.damage += deltaDamage
    }

    private fun enchantLevelBonusDamage(level: Double): Double {
        if (level <= 0.0) {
            return 0.0
        }
        return level * 0.5 + 0.5
    }
}
