package jp.awabi2048.cccontent.features.sukima_dungeon.mobs

import org.bukkit.entity.LivingEntity

/**
 * 繝繝ｳ繧ｸ繝ｧ繝ｳ蜀・・繝｢繝悶↓迚ｹ谿翫↑謖吝虚・医せ繧ｭ繝ｫ繧・き繧ｹ繧ｿ繝繝ｭ繧ｸ繝・け・峨ｒ莉倅ｸ弱☆繧九◆繧√・繧､繝ｳ繧ｿ繝ｼ繝輔ぉ繝ｼ繧ｹ縲・
 * 螳滄圀縺ｮ蜈ｷ雎｡繧ｯ繝ｩ繧ｹ縺ｯ縺薙・繝代ャ繧ｱ繝ｼ繧ｸ蜀・↓螳溯｣・＆繧後ｋ縺薙→繧呈Φ螳壹・
 */
interface SpecialBehavior {
    /**
     * 謖ｯ繧玖・縺・・隴伜挨蟄舌Ｎobs.yml遲峨〒菴ｿ逕ｨ縺輔ｌ繧九・
     */
    val id: String

    /**
     * 繧ｨ繝ｳ繝・ぅ繝・ぅ縺ｮ繧ｹ繝昴・繝ｳ譎ゅ↓荳蠎ｦ縺縺大他縺ｳ蜃ｺ縺輔ｌ繧九・
     * 繧ｹ繝・・繧ｿ繧ｹ縺ｮ蠕ｮ隱ｿ謨ｴ繧・ヱ繝ｼ繝・ぅ繧ｯ繝ｫ縺ｮ陦ｨ遉ｺ縺ｪ縺ｩ縺ｫ菴ｿ逕ｨ縲・
     */
    fun onApply(entity: LivingEntity)

    /**
     * 螳壽悄逧・↓・井ｾ具ｼ・繝・ぅ繝・け縺斐→繧・焚繝・ぅ繝・け縺斐→・牙他縺ｳ蜃ｺ縺輔ｌ繧九・
     * AI縺ｮ蛻ｶ蠕｡繧・音谿頑判謦・∝捉蝗ｲ縺ｮ繝励Ξ繧､繝､繝ｼ縺ｸ縺ｮ蠖ｱ髻ｿ縺ｪ縺ｩ縺ｫ菴ｿ逕ｨ縲・
     */
    fun onTick(entity: LivingEntity)
}
