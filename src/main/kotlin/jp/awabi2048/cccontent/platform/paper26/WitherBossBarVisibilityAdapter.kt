package jp.awabi2048.cccontent.platform.paper26

import org.bukkit.Bukkit
import org.bukkit.entity.Wither
import java.util.concurrent.atomic.AtomicBoolean

/** Paper 26.1.2の公開APIで操作できないWitherボスバーを隔離して扱う境界。 */
object WitherBossBarVisibilityAdapter {
    private val warned = AtomicBoolean(false)

    fun hide(wither: Wither): Boolean {
        val directMethodNames = listOf("setShouldDrawBossBar", "setBossBarVisible", "setShowBossBar")
        for (name in directMethodNames) {
            val hidden = runCatching {
                val method = wither.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 1 }
                    ?: return@runCatching false
                method.invoke(wither, false)
                true
            }.getOrDefault(false)
            if (hidden) return true
        }

        val hidden = runCatching {
            val handle = wither.javaClass.getMethod("getHandle").invoke(wither) ?: return@runCatching false
            val bossEvent = findBossEvent(handle) ?: return@runCatching false
            val method = bossEvent.javaClass.methods.firstOrNull { it.name == "setVisible" && it.parameterCount == 1 }
                ?: return@runCatching false
            method.invoke(bossEvent, false)
            true
        }.getOrDefault(false)

        if (!hidden && warned.compareAndSet(false, true)) {
            Bukkit.getLogger().warning(
                "[CC-Content] Paper 26.1.2 Witherボスバー境界で対応メソッドを解決できませんでした: ${wither.javaClass.name}"
            )
        }
        return hidden
    }

    private fun findBossEvent(instance: Any): Any? {
        var current: Class<*>? = instance.javaClass
        while (current != null && current != Any::class.java) {
            for (field in current.declaredFields) {
                val value = runCatching {
                    field.isAccessible = true
                    field.get(instance)
                }.getOrNull() ?: continue
                if (value.javaClass.name.endsWith("ServerBossEvent")) return value
            }
            current = current.superclass
        }
        return null
    }
}
