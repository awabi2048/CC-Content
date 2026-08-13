package jp.awabi2048.cccontent.command;

import com.awabi2048.ccsystem.api.displayeffect.VoxelParticleVisibilityMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VoxelParticleCommandSyntaxTest {
    @Test
    void acceptsVanillaStyleShortAndFullForms() {
        var shortForm = VoxelParticleCommandSyntax.INSTANCE.parse(new String[]{"flame"});
        assertEquals("minecraft:flame", shortForm.getPatternId());
        assertEquals(1, shortForm.getCount());

        var fullForm = VoxelParticleCommandSyntax.INSTANCE.parse(new String[]{
            "minecraft:crit", "~", "~1", "~-2", "0.1", "0.2", "0.3", "0.5", "4", "force"
        });
        assertEquals(4, fullForm.getCount());
        assertEquals(0.5, fullForm.getSpeed());
        assertEquals(VoxelParticleVisibilityMode.FORCE, fullForm.getVisibilityMode());
    }

    @Test
    void rejectsAmbiguousIntermediateFormsAndViewerSelectors() {
        assertThrows(IllegalArgumentException.class, () ->
            VoxelParticleCommandSyntax.INSTANCE.parse(new String[]{"flame", "~"})
        );
        assertThrows(IllegalArgumentException.class, () ->
            VoxelParticleCommandSyntax.INSTANCE.parse(new String[]{
                "flame", "~", "~", "~", "0", "0", "0", "0", "1", "normal", "@a"
            })
        );
    }
}
