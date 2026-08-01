package jp.awabi2048.cccontent.util

import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.potion.PotionEffectType
import java.util.Locale

object PotionEffectTypeResolver {
    @JvmStatic
    fun key(raw: String): NamespacedKey? =
        raw.trim()
            .takeIf(String::isNotEmpty)
            ?.lowercase(Locale.ROOT)
            ?.let(NamespacedKey::fromString)

    @JvmStatic
    fun resolve(raw: String): PotionEffectType? =
        key(raw)?.let(Registry.MOB_EFFECT::get)
}
