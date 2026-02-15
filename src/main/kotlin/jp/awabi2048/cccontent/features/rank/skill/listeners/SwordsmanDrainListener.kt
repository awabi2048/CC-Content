package jp.awabi2048.cccontent.features.rank.skill.listeners

import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectEngine
import jp.awabi2048.cccontent.features.rank.skill.handlers.SwordsmanDrainHandler
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import kotlin.random.Random

class SwordsmanDrainListener : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        if (event.entity !is LivingEntity) {
            return
        }
        if (event.entity.uniqueId == attacker.uniqueId) {
            return
        }

        val mainHandTypeName = attacker.inventory.itemInMainHand.type.name
        if (!mainHandTypeName.contains("SWORD")) {
            return
        }

        val compiledEffects = SkillEffectEngine.getCachedEffects(attacker.uniqueId) ?: return
        if (compiledEffects.profession != Profession.SWORDSMAN) {
            return
        }

        val drainEntries = compiledEffects.byType[SwordsmanDrainHandler.EFFECT_TYPE] ?: return
        if (drainEntries.isEmpty()) {
            return
        }

        val totalRatio = drainEntries.sumOf { it.effect.getDoubleParam("ratio", 0.0).coerceAtLeast(0.0) }
        val totalMaxHeal = drainEntries.sumOf { it.effect.getDoubleParam("max_heal", 0.0).coerceAtLeast(0.0) }
        val totalChance = drainEntries.sumOf { it.effect.getDoubleParam("chance", 0.0) }.coerceIn(0.0, 1.0)

        if (totalRatio <= 0.0 || totalChance <= 0.0 || totalMaxHeal <= 0.0) {
            return
        }

        val roll = Random.nextDouble()
        if (roll > totalChance) {
            return
        }

        val finalDamage = event.finalDamage.coerceAtLeast(0.0)
        if (finalDamage <= 0.0) {
            return
        }

        val maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH)?.value ?: attacker.health
        val missingHealth = (maxHealth - attacker.health).coerceAtLeast(0.0)
        if (missingHealth <= 0.0) {
            return
        }

        val healAmount = (finalDamage * totalRatio).coerceAtMost(totalMaxHeal).coerceAtMost(missingHealth)
        if (healAmount <= 0.0) {
            return
        }

        attacker.health = (attacker.health + healAmount).coerceAtMost(maxHealth)
        attacker.playSound(attacker.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.25f)
    }
}
