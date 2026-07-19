package jp.awabi2048.cccontent.features.fishing;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NaturalFishingGroundPolicyTest {
    private final Location origin = new Location(null, 0.0, 64.0, 0.0);

    @Test
    void resolvesAllEightNotificationDirectionsWithoutCoordinates() {
        assertEquals("north", NaturalFishingGroundService.directionId(origin, at(0, -10)));
        assertEquals("north_east", NaturalFishingGroundService.directionId(origin, at(10, -10)));
        assertEquals("east", NaturalFishingGroundService.directionId(origin, at(10, 0)));
        assertEquals("south_east", NaturalFishingGroundService.directionId(origin, at(10, 10)));
        assertEquals("south", NaturalFishingGroundService.directionId(origin, at(0, 10)));
        assertEquals("south_west", NaturalFishingGroundService.directionId(origin, at(-10, 10)));
        assertEquals("west", NaturalFishingGroundService.directionId(origin, at(-10, 0)));
        assertEquals("north_west", NaturalFishingGroundService.directionId(origin, at(-10, -10)));
    }

    private Location at(double x, double z) {
        return new Location(null, x, 64.0, z);
    }
}
