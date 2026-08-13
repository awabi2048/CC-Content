package jp.awabi2048.cccontent.command;

import com.awabi2048.ccsystem.api.displayeffect.DisplayParticleVisibilityMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DisplayParticleCommandSyntaxTest {
    @Test
    void acceptsVanillaStyleShortAndFullForms() {
        var shortForm = DisplayParticleCommandSyntax.INSTANCE.parse(new String[]{"ember"});
        assertEquals("cc:ember", shortForm.getPatternId());
        assertEquals(1, shortForm.getCount());

        var fullForm = DisplayParticleCommandSyntax.INSTANCE.parse(new String[]{
            "cc:spark", "~", "~1", "~-2", "0.1", "0.2", "0.3", "0.5", "4", "force"
        });
        assertEquals(4, fullForm.getCount());
        assertEquals(0.5, fullForm.getSpeed());
        assertEquals(DisplayParticleVisibilityMode.FORCE, fullForm.getVisibilityMode());
    }

    @Test
    void rejectsAmbiguousIntermediateFormsAndViewerSelectors() {
        assertThrows(IllegalArgumentException.class, () ->
            DisplayParticleCommandSyntax.INSTANCE.parse(new String[]{"ember", "~"})
        );
        assertThrows(IllegalArgumentException.class, () ->
            DisplayParticleCommandSyntax.INSTANCE.parse(new String[]{
                "ember", "~", "~", "~", "0", "0", "0", "0", "1", "normal", "@a"
            })
        );
    }
}
