package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

/**
 * モブ管理クラス
 * ダンジョン内のモブスポーン・スケーリングを管理
 */
class MobManager(private val plugin: JavaPlugin) {
    
    /**
     * モブ難易度設定
     */
    enum class MobDifficulty {
        EASY,
        NORMAL,
        HARD
    }
    
    /**
     * スポーン済みモブの記録
     */
    data class SpawnedMob(
        val entityId: Int,
        val spawnTime: Long,
        val difficulty: MobDifficulty
    )
    
    // スポーン済みモブ（ワールドUUID -> モブリスト）
    private val spawnedMobs: MutableMap<String, MutableList<SpawnedMob>> = mutableMapOf()
    
    // モブ難易度設定
    private val mobDifficulty: MutableMap<String, MobDifficulty> = mutableMapOf()
    
    /**
     * ワールドのモブ難易度を設定
     * @param worldName ワールド名
     * @param difficulty 難易度
     */
    fun setMobDifficulty(worldName: String, difficulty: MobDifficulty) {
        mobDifficulty[worldName] = difficulty
        plugin.logger.info("[SukimaDungeon] $worldName のモブ難易度を ${difficulty.name} に設定しました")
    }
    
    /**
     * ワールドのモブ難易度を取得
     * @param worldName ワールド名
     * @return 難易度（デフォルト: NORMAL）
     */
    fun getMobDifficulty(worldName: String): MobDifficulty {
        return mobDifficulty[worldName] ?: MobDifficulty.NORMAL
    }
    
    /**
     * モブをスポーン
     * @param location スポーン位置
     * @param entityType エンティティタイプ
     * @param worldName ワールド名
     * @return スポーンされたエンティティ、失敗時は null
     */
    fun spawnMob(location: Location, entityType: EntityType, worldName: String): Entity? {
        return try {
            val entity = location.world?.spawnEntity(location, entityType) ?: return null
            
            // 難易度に応じてスケーリング
            if (entity is LivingEntity) {
                val difficulty = getMobDifficulty(worldName)
                scaleMob(entity, difficulty)
            }
            
            // モブ記録に追加
            val mobList = spawnedMobs.getOrPut(worldName) { mutableListOf() }
            val difficulty = getMobDifficulty(worldName)
            mobList.add(SpawnedMob(entity.entityId, System.currentTimeMillis(), difficulty))
            
            plugin.logger.info("[SukimaDungeon] モブをスポーンしました: ${entityType.name}")
            entity
        } catch (e: Exception) {
            plugin.logger.warning("[SukimaDungeon] モブスポーン中にエラー: ${e.message}")
            null
        }
    }
    
    /**
     * モブを難易度に応じてスケーリング
     * @param entity エンティティ
     * @param difficulty 難易度
     */
    private fun scaleMob(entity: LivingEntity, difficulty: MobDifficulty) {
        try {
            val maxHealthAttr = entity.getAttribute(Attribute.valueOf("GENERIC_MAX_HEALTH")) ?: return
            val attackDamageAttr = entity.getAttribute(Attribute.valueOf("GENERIC_ATTACK_DAMAGE")) ?: return
            
            when (difficulty) {
                MobDifficulty.EASY -> {
                    // 体力 70%、攻撃力 50%
                    maxHealthAttr.baseValue = maxHealthAttr.baseValue * 0.7
                    attackDamageAttr.baseValue = attackDamageAttr.baseValue * 0.5
                }
                MobDifficulty.NORMAL -> {
                    // そのまま
                }
                MobDifficulty.HARD -> {
                    // 体力 150%、攻撃力 150%
                    maxHealthAttr.baseValue = maxHealthAttr.baseValue * 1.5
                    attackDamageAttr.baseValue = attackDamageAttr.baseValue * 1.5
                }
            }
            
            entity.health = maxHealthAttr.baseValue
        } catch (e: Exception) {
            plugin.logger.warning("[SukimaDungeon] モブスケーリング中にエラー: ${e.message}")
        }
    }
    
    /**
     * モブを複数プレイヤーの数に応じてスケーリング調整
     * @param entity エンティティ
     * @param playerCount プレイヤー数
     */
    fun scaleForPlayerCount(entity: LivingEntity, playerCount: Int) {
        if (playerCount <= 1) return  // シングルプレイは調整なし
        
        try {
            val maxHealthAttr = entity.getAttribute(Attribute.valueOf("GENERIC_MAX_HEALTH")) ?: return
            val attackDamageAttr = entity.getAttribute(Attribute.valueOf("GENERIC_ATTACK_DAMAGE")) ?: return
            
            // プレイヤー数に応じて難易度を調整
            val scaleFactor = 1.0 + (playerCount - 1) * 0.25  // 1プレイヤーごとに 25% 増加
            
            maxHealthAttr.baseValue = maxHealthAttr.baseValue * scaleFactor
            attackDamageAttr.baseValue = attackDamageAttr.baseValue * scaleFactor
            
            entity.health = maxHealthAttr.baseValue
        } catch (e: Exception) {
            plugin.logger.warning("[SukimaDungeon] プレイヤー数スケーリング中にエラー: ${e.message}")
        }
    }
    
    /**
     * ワールド内の全モブを削除
     * @param worldName ワールド名
     */
    fun removeAllMobs(worldName: String) {
        val mobList = spawnedMobs[worldName] ?: return
        
        val world = org.bukkit.Bukkit.getWorld(worldName) ?: return
        
        for (mob in mobList) {
            // エンティティIDから該当エンティティを検索
            val entity = world.entities.find { it.entityId == mob.entityId } ?: continue
            entity.remove()
        }
        
        spawnedMobs.remove(worldName)
        plugin.logger.info("[SukimaDungeon] $worldName 内の全モブを削除しました")
    }
    
    /**
     * ワールド内のモブ数を取得
     * @param worldName ワールド名
     * @return モブ数
     */
    fun getMobCount(worldName: String): Int {
        return spawnedMobs[worldName]?.size ?: 0
    }
    
    /**
     * ワールド内の生存モブ数を取得
     * @param worldName ワールド名
     * @return 生存モブ数
     */
    fun getLiveMobCount(worldName: String): Int {
        val mobList = spawnedMobs[worldName] ?: return 0
        val world = org.bukkit.Bukkit.getWorld(worldName) ?: return 0
        
        return mobList.count { mob ->
            val entity = world.entities.find { it.entityId == mob.entityId }
            entity != null && !entity.isDead
        }
    }
    
    /**
     * クリーンアップ
     */
    fun cleanup() {
        for (worldName in spawnedMobs.keys) {
            removeAllMobs(worldName)
        }
        spawnedMobs.clear()
        mobDifficulty.clear()
    }
}
