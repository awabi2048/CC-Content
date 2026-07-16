package jp.awabi2048.cccontent.features.resourcecollection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionRewardGuardTest {
    @Test
    void sameEventIsReservedOnlyOnce() {
        CollectionRewardGuard guard = new CollectionRewardGuard();
        CollectionRewardKey key = new CollectionRewardKey(CollectionAction.HARVEST, "player", "event:1");

        assertTrue(guard.tryReserve(key));
        assertFalse(guard.tryReserve(key));
    }

    @Test
    void harvestAndCraftEventsAreIndependent() {
        CollectionRewardGuard guard = new CollectionRewardGuard();

        assertTrue(guard.tryReserve(new CollectionRewardKey(CollectionAction.HARVEST, "player", "event:1")));
        assertTrue(guard.tryReserve(new CollectionRewardKey(CollectionAction.CRAFT, "player", "event:1")));
        assertFalse(guard.tryReserve(new CollectionRewardKey(CollectionAction.HARVEST, "player", "event:1")));
    }
}
