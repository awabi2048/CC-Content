package jp.awabi2048.cccontent;

import jp.awabi2048.cccontent.util.PotionEffectTypeResolver;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PotionEffectTypeResolverTest {
    @Test
    void normalizesLegacyStyleEffectNamesToMinecraftKeys() {
        assertEquals(NamespacedKey.minecraft("speed"), PotionEffectTypeResolver.key(" SPEED "));
        assertEquals(NamespacedKey.minecraft("slowness"), PotionEffectTypeResolver.key("minecraft:SLOWNESS"));
        assertNull(PotionEffectTypeResolver.key(""));
        assertNull(PotionEffectTypeResolver.key("not a valid key"));
    }
}
