package jp.awabi2048.cccontent.features.rank.skill

import jp.awabi2048.cccontent.features.rank.profession.Profession
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.inventory.ItemStack
import java.util.UUID

data class EffectContext(
    val player: Player,
    val playerUuid: UUID,
    val profession: Profession,
    val skillId: String,
    val skillEffect: SkillEffect,
    val rawEvent: Event? = null,
    val currentLevel: Int = 1
) {
    fun <T : Event> getEvent(): T? {
        @Suppress("UNCHECKED_CAST")
        return rawEvent as? T
    }

    fun hasParam(key: String): Boolean = skillEffect.params.containsKey(key)
}
