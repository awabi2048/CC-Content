package jp.awabi2048.cccontent.features.cooking;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CookingVanillaDefinitionsTest {
    @Test
    void containsEverySpecifiedCraftingFurnaceAndSmokerDefinition() {
        var definitions = CookingVanillaDefinitions.INSTANCE.getAll();
        assertEquals(15, definitions.size());
        assertEquals(7, definitions.stream().filter(it -> it.getExperience() > 0).count());
        assertEquals(Set.of(CookingStation.CRAFTING, CookingStation.FURNACE, CookingStation.SMOKER),
            definitions.stream().map(VanillaCookingDefinition::getStation).collect(Collectors.toSet()));
        assertTrue(definitions.stream().anyMatch(it -> it.getId().equals("roasted_coffee") && it.getCookTicks() == 200));
        assertTrue(definitions.stream().anyMatch(it -> it.getId().equals("carrot_cookie") && it.getOutputAmount() == 4));
        assertTrue(definitions.stream().anyMatch(it -> it.getId().equals("smoked_salmon") && it.getCookTicks() == 100));
    }
}
