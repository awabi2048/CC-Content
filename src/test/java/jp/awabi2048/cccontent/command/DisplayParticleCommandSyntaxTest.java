package jp.awabi2048.cccontent.command;

import com.awabi2048.ccsystem.api.displayeffect.DisplayParticleVisibilityMode;
import com.awabi2048.ccsystem.api.displayeffect.DisplayParticleCollisionMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DisplayParticleCommandSyntaxTest {
    @Test
    void acceptsComposedShortAndFullForms() {
        var shortForm = DisplayParticleCommandSyntax.INSTANCE.parse(new String[]{"ember", "static", "none"});
        assertEquals("cc:ember", shortForm.getPatternId());
        assertEquals("cc:static", shortForm.getMotionId());
        assertEquals(DisplayParticleCollisionMode.NONE, shortForm.getCollisionMode());
        assertEquals(1, shortForm.getCount());

        var fullForm = DisplayParticleCommandSyntax.INSTANCE.parse(new String[]{
            "cc:spark", "burst", "remove", "~", "~1", "~-2", "0.1", "0.2", "0.3", "0.5", "4", "force",
            "--", "motion.initial-velocity=0,0.03,0", "motion.radial-speed=0.15"
        });
        assertEquals(4, fullForm.getCount());
        assertEquals(0.5, fullForm.getSpeed());
        assertEquals(DisplayParticleVisibilityMode.FORCE, fullForm.getVisibilityMode());
        assertEquals(0.15, fullForm.getMotionProperties().getRadialSpeed());
    }

    @Test
    void rejectsAmbiguousIntermediateFormsAndViewerSelectors() {
        assertThrows(IllegalArgumentException.class, () ->
            DisplayParticleCommandSyntax.INSTANCE.parse(new String[]{"ember", "static"})
        );
        assertThrows(IllegalArgumentException.class, () ->
            DisplayParticleCommandSyntax.INSTANCE.parse(new String[]{
                "ember", "static", "none", "~", "~", "~", "0", "0", "0", "0", "1", "normal", "@a"
            })
        );
        assertThrows(IllegalArgumentException.class, () ->
            DisplayParticleCommandSyntax.INSTANCE.parse(new String[]{
                "ember", "static", "none", "--", "motion.unknown=1"
            })
        );
    }
}
