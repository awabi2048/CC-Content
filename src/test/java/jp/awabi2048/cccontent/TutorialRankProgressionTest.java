package jp.awabi2048.cccontent;

import jp.awabi2048.cccontent.features.rank.tutorial.task.NetherPortalCreationPredicate;
import jp.awabi2048.cccontent.features.rank.tutorial.task.TaskRequirement;
import jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialRankRequirementRegistry;
import org.bukkit.Material;
import org.bukkit.event.world.PortalCreateEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TutorialRankProgressionTest {
    @Test
    void fixedRankRequirementsAreDefined() {
        TaskRequirement newbie = TutorialRankRequirementRegistry.INSTANCE.getRequirement("NEWBIE");
        assertEquals(15, newbie.getPlayTimeMin());
        assertTrue(newbie.getRequiresMyWorldCreated());

        TaskRequirement visitor = TutorialRankRequirementRegistry.INSTANCE.getRequirement("visitor");
        assertEquals(20, visitor.getDiamondOreMines());
        assertTrue(visitor.getRequiresNetherPortalIgnited());
    }

    @Test
    void unknownRankIdFailsInsteadOfBecomingEmptyRequirement() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> TutorialRankRequirementRegistry.INSTANCE.getRequirement("UNKNOWN")
        );
        assertTrue(exception.getMessage().contains("UNKNOWN"));
    }

    @Test
    void portalRequirementNeedsFireReasonAndGeneratedPortalBlocks() {
        assertTrue(NetherPortalCreationPredicate.INSTANCE.isEstablished(
            PortalCreateEvent.CreateReason.FIRE, List.of(Material.NETHER_PORTAL)
        ));
        assertFalse(NetherPortalCreationPredicate.INSTANCE.isEstablished(
            PortalCreateEvent.CreateReason.FIRE, List.of(Material.OBSIDIAN)
        ));
        assertFalse(NetherPortalCreationPredicate.INSTANCE.isEstablished(
            PortalCreateEvent.CreateReason.END_PLATFORM, List.of(Material.NETHER_PORTAL)
        ));
    }
}
