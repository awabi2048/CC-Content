package jp.awabi2048.cccontent.features.arena.mechanic

interface ArenaThemeMechanic {
    fun onStageReady(context: ArenaMechanicContext) {}
    fun onSessionStarted(context: ArenaMechanicContext) {}
    fun onWaveStarted(context: ArenaMechanicContext, wave: Int) {}
    fun onWaveCleared(context: ArenaMechanicContext, wave: Int) {}
    fun onTick(context: ArenaMechanicContext, currentTick: Long) {}
    fun onSessionEnded(context: ArenaMechanicContext, success: Boolean) {}
    fun dispose() {}
}

object NoopArenaThemeMechanic : ArenaThemeMechanic
