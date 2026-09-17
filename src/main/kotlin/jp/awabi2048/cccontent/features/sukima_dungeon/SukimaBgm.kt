package jp.awabi2048.cccontent.features.sukima_dungeon

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.bgm.BgmRequest
import com.awabi2048.ccsystem.api.bgm.BgmSource
import jp.awabi2048.cccontent.CCContent
import org.bukkit.entity.Player

/**
 * スキマダンジョンBGMの取得・解放口。
 * 実再生・優先解決・寿命管理はCC-SystemのBgmServiceへ移譲する。
 * 旧BGMManager.play(player, "default")相当（設定bgm.default、既定カテゴリ・音量1.0・ピッチ1.0）。
 */
internal object SukimaBgm {
    private const val CONFIG_ID = "default"

    /** ダンジョンBGM予約を取得する。設定不正時は警告のみで何もしない。 */
    fun play(player: Player) {
        val request = resolveRequest() ?: return
        CCSystem.getAPI().getBgmService().acquire(player, BgmSource.SUKIMA, request)
    }

    /** ダンジョンBGM予約を解放する。下位予約（ワールドBGM等）があれば自動復帰する。 */
    fun release(player: Player) {
        CCSystem.getAPI().getBgmService().release(player, BgmSource.SUKIMA)
    }

    private fun resolveRequest(): BgmRequest? {
        val plugin = CCContent.instance
        val config = SukimaConfigHelper.getConfig(plugin)
        val key = config.getString("bgm.$CONFIG_ID.key")
        val duration = config.getInt("bgm.$CONFIG_ID.duration", 0)
        if (key.isNullOrBlank()) {
            plugin.logger.warning("[SukimaDungeon] BGM設定が見つかりません: id=$CONFIG_ID")
            return null
        }
        if (duration <= 0) {
            plugin.logger.warning("[SukimaDungeon] BGM再生時間が不正です: id=$CONFIG_ID duration=$duration")
            return null
        }
        return try {
            BgmRequest(soundKey = key, loopTicks = duration * 20L)
        } catch (e: IllegalArgumentException) {
            plugin.logger.warning("[SukimaDungeon] BGM要求が不正です: ${e.message}")
            null
        }
    }
}
