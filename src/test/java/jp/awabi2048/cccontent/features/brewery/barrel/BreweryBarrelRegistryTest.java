package jp.awabi2048.cccontent.features.brewery.barrel;

import jp.awabi2048.cccontent.features.brewery.model.BarrelSize;
import jp.awabi2048.cccontent.features.brewery.model.BreweryLocationKey;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BreweryBarrelRegistryTest {
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void rejectsOverlappingStructuresAndKeepsOriginalReverseLookup() {
        BreweryBarrelRegistry registry = new BreweryBarrelRegistry();
        BreweryLocationKey shared = key(1, 64, 1);
        BreweryBarrel first = barrel(shared, key(2, 64, 1));
        BreweryBarrel overlap = barrel(shared, key(3, 64, 1));

        assertTrue(registry.register(first));
        assertFalse(registry.register(overlap));
        assertEquals(first, registry.findByBlock(shared));
        assertNull(registry.findById(overlap.getId()));
    }

    @Test
    void unregisterReleasesEveryMemberForReuse() {
        BreweryBarrelRegistry registry = new BreweryBarrelRegistry();
        BreweryLocationKey firstMember = key(10, 64, 10);
        BreweryLocationKey secondMember = key(11, 64, 10);
        BreweryBarrel first = barrel(firstMember, secondMember);

        assertTrue(registry.register(first));
        assertEquals(first, registry.unregister(first.getId()));
        assertNull(registry.findByBlock(firstMember));
        assertNull(registry.findByBlock(secondMember));
        assertTrue(registry.register(barrel(firstMember, secondMember)));
    }

    private static BreweryBarrel barrel(BreweryLocationKey first, BreweryLocationKey second) {
        return new BreweryBarrel(
            UUID.randomUUID(), BarrelSize.SMALL, first, second,
            BlockFace.NORTH, "oak", Set.of(first, second)
        );
    }

    private static BreweryLocationKey key(int x, int y, int z) {
        return new BreweryLocationKey(WORLD, x, y, z);
    }
}
