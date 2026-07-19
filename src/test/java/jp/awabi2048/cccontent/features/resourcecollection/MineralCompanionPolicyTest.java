package jp.awabi2048.cccontent.features.resourcecollection;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MineralCompanionPolicyTest {
    @Test
    void resolvesOnlyFromBiomeBandAndAltitudeBand() {
        var dry = MineralCompanionPolicy.INSTANCE.inspect(
            World.Environment.NORMAL, "minecraft:badlands", 64
        );
        var deep = MineralCompanionPolicy.INSTANCE.inspect(
            World.Environment.NORMAL, "minecraft:plains", -32
        );
        var nether = MineralCompanionPolicy.INSTANCE.inspect(
            World.Environment.NETHER, "minecraft:warped_forest", 64
        );

        assertEquals(MineralBiomeBand.DRY, dry.getBiome());
        assertEquals("rock_salt", dry.getResourceId());
        assertEquals(MineralAltitudeBand.DEEP, deep.getAltitude());
        assertEquals("calcite_fragment", deep.getResourceId());
        assertEquals(MineralBiomeBand.NETHER, nether.getBiome());
        assertEquals("sulfur", nether.getResourceId());
    }

    @Test
    void classifiesBoundaryHeightsDeterministically() {
        assertEquals(MineralAltitudeBand.DEEP, inspectAt(-1).getAltitude());
        assertEquals(MineralAltitudeBand.MIDDLE, inspectAt(0).getAltitude());
        assertEquals(MineralAltitudeBand.SHALLOW, inspectAt(32).getAltitude());
        assertEquals(MineralAltitudeBand.HIGH, inspectAt(96).getAltitude());
    }

    private MineralInspectionResult inspectAt(int y) {
        return MineralCompanionPolicy.INSTANCE.inspect(
            World.Environment.NORMAL, "minecraft:plains", y
        );
    }
}
