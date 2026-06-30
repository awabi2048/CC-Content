package jp.awabi2048.cccontent.features.arena.mechanic

import jp.awabi2048.cccontent.features.arena.ArenaActionMarker
import org.bukkit.entity.LivingEntity

interface ArenaThemeMechanic {
    fun onStageReady(context: ArenaMechanicContext) {}
    fun onActionMarkersInitialized(context: ArenaMechanicContext) {}
    fun onSessionStarted(context: ArenaMechanicContext) {}
    fun onWaveStarted(context: ArenaMechanicContext, wave: Int) {}
    fun onWaveCleared(context: ArenaMechanicContext, wave: Int) {}
    fun onActionMarkerTriggered(context: ArenaMechanicContext, marker: ArenaActionMarker): Boolean = false
    fun onMobKilled(context: ArenaMechanicContext, mob: LivingEntity) {}
    fun hasCustomBarrierRestartObjective(context: ArenaMechanicContext): Boolean = false
    fun barrierRestartGate(context: ArenaMechanicContext): ArenaMechanicBarrierGateResult = ArenaMechanicBarrierGateResult.allowed()
    fun barrierRestartProgress(context: ArenaMechanicContext): ArenaMechanicObjectiveProgress? = null
    fun onTick(context: ArenaMechanicContext, currentTick: Long) {}
    fun onSessionEnded(context: ArenaMechanicContext, success: Boolean) {}
    fun dispose() {}
}

object NoopArenaThemeMechanic : ArenaThemeMechanic
