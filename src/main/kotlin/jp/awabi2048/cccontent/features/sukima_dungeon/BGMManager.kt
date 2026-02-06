package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

/**
 * BGM管理クラス
 * ダンジョン内のバックグラウンドミュージック（BGM）を管理
 */
class BGMManager(private val plugin: JavaPlugin) {
    
    /**
     * BGM定義
     */
    data class BGMTrack(
        val sound: Sound,
        val volume: Float = 1.0f,
        val pitch: Float = 1.0f,
        val category: String = "ambient"  // ambient, music, record等
    )
    
    /**
     * BGMタイプ定義
     */
    enum class BGMType {
        EXPLORATION,   // 探索中
        BOSS_BATTLE,   // ボス戦
        DANGER,        // 危機的状況
        VICTORY,       // 勝利
        DEFEAT         // 敗北
    }
    
    // BGMトラック（タイプ -> BGMトラック）
    private val bgmTracks: MutableMap<BGMType, BGMTrack> = mutableMapOf()
    
    // 現在再生中のBGM（プレイヤーUUID -> BGMタイプ）
    private val activeBGM: MutableMap<UUID, BGMType> = mutableMapOf()
    
    init {
        initializeDefaultBGM()
    }
    
    /**
     * デフォルトBGMを初期化
     */
    private fun initializeDefaultBGM() {
        bgmTracks[BGMType.EXPLORATION] = BGMTrack(Sound.MUSIC_DISC_STAL, 0.5f, 1.0f)
        bgmTracks[BGMType.BOSS_BATTLE] = BGMTrack(Sound.MUSIC_DISC_WARD, 0.6f, 1.0f)
        bgmTracks[BGMType.DANGER] = BGMTrack(Sound.MUSIC_DISC_PIGSTEP, 0.7f, 1.2f)
        bgmTracks[BGMType.VICTORY] = BGMTrack(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
        bgmTracks[BGMType.DEFEAT] = BGMTrack(Sound.ENTITY_WARDEN_DEATH, 0.8f, 0.8f)
    }
    
    /**
     * BGMを再生
     * @param player プレイヤー
     * @param bgmType BGMタイプ
     */
    fun playBGM(player: Player, bgmType: BGMType) {
        val bgmTrack = bgmTracks[bgmType] ?: return
        
        // 現在のBGMを停止
        stopBGM(player)
        
        // 新しいBGMを再生
        player.playSound(
            player.location,
            bgmTrack.sound,
            bgmTrack.volume,
            bgmTrack.pitch
        )
        
        activeBGM[player.uniqueId] = bgmType
        plugin.logger.info("[SukimaDungeon] ${player.name} に ${bgmType.name} BGM を再生しました")
    }
    
    /**
     * BGMを停止
     * @param player プレイヤー
     */
    fun stopBGM(player: Player) {
        activeBGM.remove(player.uniqueId)
        plugin.logger.info("[SukimaDungeon] ${player.name} のBGMを停止しました")
    }
    
    /**
     * 全プレイヤーのBGMを停止
     */
    fun stopAllBGM() {
        activeBGM.clear()
    }
    
    /**
     * プレイヤーが再生中のBGMタイプを取得
     * @param player プレイヤー
     * @return BGMタイプ、再生中でない場合は null
     */
    fun getActiveBGM(player: Player): BGMType? {
        return activeBGM[player.uniqueId]
    }
    
    /**
     * BGMトラックを登録
     * @param bgmType BGMタイプ
     * @param bgmTrack BGMトラック
     */
    fun registerBGMTrack(bgmType: BGMType, bgmTrack: BGMTrack) {
        bgmTracks[bgmType] = bgmTrack
        plugin.logger.info("[SukimaDungeon] BGMトラックを登録しました: ${bgmType.name}")
    }
    
    /**
     * BGMトラックを取得
     * @param bgmType BGMタイプ
     * @return BGMトラック、見つからない場合は null
     */
    fun getBGMTrack(bgmType: BGMType): BGMTrack? {
        return bgmTracks[bgmType]
    }
    
    /**
     * 全BGMトラックを取得
     * @return BGMトラックのマップ
     */
    fun getAllBGMTracks(): Map<BGMType, BGMTrack> {
        return bgmTracks.toMap()
    }
    
    /**
     * サウンドエフェクト（SEとして）を再生
     * @param player プレイヤー
     * @param sound サウンド
     * @param volume 音量
     * @param pitch ピッチ
     */
    fun playSoundEffect(
        player: Player,
        sound: Sound,
        volume: Float = 1.0f,
        pitch: Float = 1.0f
    ) {
        player.playSound(player.location, sound, volume, pitch)
    }
    
    /**
     * 特定位置でサウンドを再生（全プレイヤー向け）
     * @param location 位置
     * @param sound サウンド
     * @param volume 音量
     * @param pitch ピッチ
     * @param radius 再生範囲（ブロック）
     */
    fun playWorldSound(
        location: Location,
        sound: Sound,
        volume: Float = 1.0f,
        pitch: Float = 1.0f,
        radius: Double = 64.0
    ) {
        val world = location.world ?: return
        
        for (player in world.players) {
            if (player.location.distance(location) <= radius) {
                player.playSound(location, sound, volume, pitch)
            }
        }
    }
    
    /**
     * クリーンアップ
     */
    fun cleanup() {
        stopAllBGM()
        bgmTracks.clear()
        initializeDefaultBGM()
    }
}
