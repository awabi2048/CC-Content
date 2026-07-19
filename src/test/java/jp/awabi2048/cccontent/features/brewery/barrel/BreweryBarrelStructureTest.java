package jp.awabi2048.cccontent.features.brewery.barrel;

import jp.awabi2048.cccontent.features.brewery.model.BarrelSize;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreweryBarrelStructureTest {
    @Test
    void smallAndLargeStructuresHaveFixedIndependentShapes() {
        assertEquals(8, BreweryBarrelStructure.INSTANCE.cells(BarrelSize.SMALL).size());
        assertEquals(40, BreweryBarrelStructure.INSTANCE.cells(BarrelSize.BIG).size());
    }

    @Test
    void everySupportedWoodResolvesAllRequiredMaterials() {
        for (String wood : BreweryBarrelStructure.INSTANCE.getSUPPORTED_WOODS()) {
            for (BarrelBlockRole role : BarrelBlockRole.values()) {
                assertNotNull(BreweryBarrelStructure.INSTANCE.material(wood, role), wood + " / " + role);
            }
        }
    }
}
