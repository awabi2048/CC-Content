package jp.awabi2048.cccontent.features.sukima_dungeon

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 繝｡繝九Η繝ｼ・・UI・峨・繧ｯ繝ｪ繝・け縺ｫ蟇ｾ縺吶ｋ繧ｯ繝ｼ繝ｫ繧ｿ繧､繝繧堤ｮ｡逅・☆繧九す繝ｳ繧ｰ繝ｫ繝医Φ繧ｯ繝ｩ繧ｹ縲・
 * 騾｣謇薙↓繧医ｋ驥崎､・・逅・ｄ諢丞峙縺励↑縺・嫌蜍輔ｒ髦ｲ豁｢縺励∪縺吶・
 */
object MenuCooldownManager {
    // 繝励Ξ繧､繝､繝ｼUUID縺ｨ譛邨ゅけ繝ｪ繝・け譎ょ綾・医Α繝ｪ遘抵ｼ峨・繝槭ャ繝・
    private val lastClickMap = ConcurrentHashMap<UUID, Long>()
    
    // 繧ｯ繝ｼ繝ｫ繧ｿ繧､繝・医Α繝ｪ遘抵ｼ峨・tick逶ｸ蠖難ｼ・tick=50ms * 2 = 100ms・・
    private const val COOLDOWN_MS = 100L

    /**
     * 繝励Ξ繧､繝､繝ｼ縺後け繝ｼ繝ｫ繧ｿ繧､繝荳ｭ縺九←縺・°繧貞愛螳壹＠縲√け繝ｼ繝ｫ繧ｿ繧､繝荳ｭ縺ｧ縺ｪ縺代ｌ縺ｰ譎ょ綾繧呈峩譁ｰ縺励∪縺吶・
     * @param uuid 繝励Ξ繧､繝､繝ｼ縺ｮUUID
     * @return 繧ｯ繝ｼ繝ｫ繧ｿ繧､繝荳ｭ縺ｮ蝣ｴ蜷医・ true縲√◎縺・〒縺ｪ縺・ｴ蜷医・ false
     */
    fun checkAndSetCooldown(uuid: UUID): Boolean {
        val now = System.currentTimeMillis()
        val lastClick = lastClickMap.getOrDefault(uuid, 0L)
        
        if (now - lastClick < COOLDOWN_MS) {
            return true
        }
        
        lastClickMap[uuid] = now
        return false
    }

    /**
     * 繝励Ξ繧､繝､繝ｼ縺後Ο繧ｰ繧｢繧ｦ繝医＠縺滄圀縺ｪ縺ｩ縺ｫ繝・・繧ｿ繧貞炎髯､縺励∪縺吶・
     */
    fun clearCooldown(uuid: UUID) {
        lastClickMap.remove(uuid)
    }
}
