package jp.awabi2048.cccontent.features.arena.mechanic

import jp.awabi2048.cccontent.features.arena.ArenaSession
import jp.awabi2048.cccontent.features.arena.mechanic.natura.NaturaArenaMechanic
import jp.awabi2048.cccontent.features.arena.mechanic.nether.NetherArenaMechanic
import jp.awabi2048.cccontent.features.arena.mechanic.ocean.OceanMonumentArenaMechanic
import org.bukkit.plugin.java.JavaPlugin

class ArenaThemeMechanicCoordinator(private val plugin: JavaPlugin) {
    private val mechanicsByWorld = mutableMapOf<String, ArenaThemeMechanic>()

    fun registerStageReady(session: ArenaSession) {
        val mechanic = createMechanic(session)
        mechanicsByWorld[session.worldName]?.dispose()
        mechanicsByWorld[session.worldName] = mechanic
        mechanic.onStageReady(context(session))
    }

    fun notifySessionStarted(session: ArenaSession) {
        mechanicsByWorld[session.worldName]?.onSessionStarted(context(session))
    }

    fun notifyWaveStarted(session: ArenaSession, wave: Int) {
        mechanicsByWorld[session.worldName]?.onWaveStarted(context(session), wave)
    }

    fun notifyWaveCleared(session: ArenaSession, wave: Int) {
        mechanicsByWorld[session.worldName]?.onWaveCleared(context(session), wave)
    }

    fun tick(sessions: Collection<ArenaSession>, currentTick: Long) {
        sessions.forEach { session ->
            mechanicsByWorld[session.worldName]?.onTick(context(session), currentTick)
        }
    }

    fun unregister(session: ArenaSession, success: Boolean) {
        val mechanic = mechanicsByWorld.remove(session.worldName) ?: return
        val context = context(session)
        runCatching { mechanic.onSessionEnded(context, success) }
            .onFailure { plugin.logger.warning("[Arena] Theme mechanic session-end hook failed: world=${session.worldName} theme=${session.themeId} error=${it.message}") }
        runCatching { mechanic.dispose() }
            .onFailure { plugin.logger.warning("[Arena] Theme mechanic dispose failed: world=${session.worldName} theme=${session.themeId} error=${it.message}") }
    }

    fun unregisterAll() {
        mechanicsByWorld.values.forEach { mechanic ->
            runCatching { mechanic.dispose() }
        }
        mechanicsByWorld.clear()
    }

    private fun createMechanic(session: ArenaSession): ArenaThemeMechanic {
        return when (session.themeId) {
            "nether" -> NetherArenaMechanic(plugin)
            "ocean_monument" -> OceanMonumentArenaMechanic(plugin)
            "natura" -> NaturaArenaMechanic(plugin)
            else -> NoopArenaThemeMechanic
        }
    }

    private fun context(session: ArenaSession): ArenaMechanicContext {
        return ArenaMechanicContext(plugin, session)
    }
}
