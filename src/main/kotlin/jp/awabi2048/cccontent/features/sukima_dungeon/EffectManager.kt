package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Entity
import org.bukkit.plugin.java.JavaPlugin

/**
 * エフェクト管理クラス
 * ダンジョン内のパーティクルエフェクト・ビジュアルエフェクトを管理
 */
class EffectManager(private val plugin: JavaPlugin) {
    
    /**
     * エフェクトタイプ定義
     */
    enum class EffectType {
        PORTAL_ENTER,      // ポータル進入
        PORTAL_EXIT,       // ポータル脱出
        BOSS_SPAWN,        // ボススポーン
        CRITICAL_HIT,      // クリティカルヒット
        HEALING,           // 回復
        BUFF_APPLIED,      // バフ付与
        DEBUFF_APPLIED,    // デバフ付与
        TREASURE_FOUND,    // 宝箱発見
        DANGER_WARNING,    // 危機警告
        VICTORY,           // 勝利
        DEFEAT             // 敗北
    }
    
    /**
     * 指定位置にエフェクトを再生
     * @param location 位置
     * @param effectType エフェクトタイプ
     * @param count パーティクル数
     */
    fun playEffect(location: Location, effectType: EffectType, count: Int = 10) {
        val world = location.world ?: return
        
        when (effectType) {
            EffectType.PORTAL_ENTER -> {
                world.spawnParticle(Particle.PORTAL, location, count, 0.5, 0.5, 0.5, 0.5)
            }
            EffectType.PORTAL_EXIT -> {
                world.spawnParticle(Particle.REVERSE_PORTAL, location, count, 0.5, 0.5, 0.5, 0.5)
            }
            EffectType.BOSS_SPAWN -> {
                world.spawnParticle(Particle.EXPLOSION, location, count, 1.0, 1.0, 1.0, 1.0)
                world.spawnParticle(Particle.FLAME, location, count / 2, 0.8, 0.8, 0.8, 0.3)
            }
            EffectType.CRITICAL_HIT -> {
                world.spawnParticle(Particle.CRIT, location, count, 0.3, 0.3, 0.3, 0.5)
            }
            EffectType.HEALING -> {
                world.spawnParticle(Particle.HEART, location, count, 0.5, 0.5, 0.5, 0.2)
            }
            EffectType.BUFF_APPLIED -> {
                world.spawnParticle(Particle.HAPPY_VILLAGER, location, count / 2, 0.3, 0.3, 0.3, 0.1)
            }
            EffectType.DEBUFF_APPLIED -> {
                world.spawnParticle(Particle.SMOKE, location, count, 0.5, 0.5, 0.5, 0.1)
            }
            EffectType.TREASURE_FOUND -> {
                world.spawnParticle(Particle.END_ROD, location, count, 0.3, 0.3, 0.3, 0.3)
                world.spawnParticle(Particle.ENCHANT, location, count / 2, 0.5, 0.5, 0.5, 0.1)
            }
            EffectType.DANGER_WARNING -> {
                world.spawnParticle(Particle.DUST, location, count, 0.5, 0.5, 0.5, 1.0)
            }
            EffectType.VICTORY -> {
                world.spawnParticle(Particle.FIREWORK, location, count, 1.0, 1.0, 1.0, 0.5)
                world.spawnParticle(Particle.HAPPY_VILLAGER, location, count, 0.5, 0.5, 0.5, 0.2)
            }
            EffectType.DEFEAT -> {
                world.spawnParticle(Particle.SMOKE, location, count, 1.0, 1.0, 1.0, 0.3)
                world.spawnParticle(Particle.FALLING_DUST, location, count / 2, 0.5, 0.5, 0.5, 0.2)
            }
        }
    }
    
    /**
     * エンティティの周囲にエフェクトを再生
     * @param entity エンティティ
     * @param effectType エフェクトタイプ
     * @param count パーティクル数
     * @param radius 半径
     */
    fun playEntityEffect(entity: Entity, effectType: EffectType, count: Int = 15, radius: Double = 1.0) {
        val location = entity.location
        val world = location.world ?: return
        
        // エンティティの足元と頭部にエフェクト
        val baseLocation = location.clone()
        val topLocation = location.clone().add(0.0, entity.height, 0.0)
        
        playEffect(baseLocation.add(0.0, -0.5, 0.0), effectType, count / 2)
        playEffect(topLocation, effectType, count / 2)
    }
    
    /**
     * 2点間にトレイルエフェクトを再生
     * @param startLocation 開始位置
     * @param endLocation 終了位置
     * @param distance 間隔（ブロック）
     * @param particleCount パーティクル数（1ブロックあたり）
     */
    fun playTrailEffect(
        startLocation: Location,
        endLocation: Location,
        distance: Double = 0.5,
        particleCount: Int = 2
    ) {
        val world = startLocation.world ?: return
        if (endLocation.world != world) return
        
        val totalDistance = startLocation.distance(endLocation)
        val steps = (totalDistance / distance).toInt()
        
        for (i in 0..steps) {
            val progress = if (steps > 0) i.toDouble() / steps else 0.0
            
            val x = startLocation.x + (endLocation.x - startLocation.x) * progress
            val y = startLocation.y + (endLocation.y - startLocation.y) * progress
            val z = startLocation.z + (endLocation.z - startLocation.z) * progress
            
            val location = Location(world, x, y, z)
            world.spawnParticle(Particle.END_ROD, location, particleCount, 0.1, 0.1, 0.1, 0.1)
        }
    }
    
    /**
     * 円形エフェクトを再生
     * @param location 中心位置
     * @param effectType エフェクトタイプ
     * @param radius 半径
     * @param particleCount パーティクル数（1周あたり）
     */
    fun playCircleEffect(
        location: Location,
        effectType: EffectType,
        radius: Double = 2.0,
        particleCount: Int = 20
    ) {
        val world = location.world ?: return
        
        for (i in 0 until particleCount) {
            val angle = 2.0 * Math.PI * i / particleCount
            
            val x = location.x + radius * Math.cos(angle)
            val z = location.z + radius * Math.sin(angle)
            
            val pointLocation = Location(world, x, location.y, z)
            playEffect(pointLocation, effectType, 1)
        }
    }
}
