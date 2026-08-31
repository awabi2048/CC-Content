package jp.awabi2048.cccontent.features.processing;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProcessingEquipmentServiceTest {
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ProcessingLocationKey BARREL = new ProcessingLocationKey(WORLD, 10, 64, 20);
    private static final ProcessingLocationKey SIGN = new ProcessingLocationKey(WORLD, 11, 64, 20);

    @Test
    void resolvesAnyMemberToOneCanonicalEquipment() {
        ProcessingEquipmentService service = service();

        ProcessingEquipment equipment = service.findAt(SIGN, ProcessingEquipmentCapability.FERMENTATION);

        assertNotNull(equipment);
        assertEquals(BARREL, equipment.getCanonicalLocation());
        assertEquals("brewery:fermentation_barrel", equipment.getId());
    }

    @Test
    void onlyOneClientCanHoldAnEquipmentLease() {
        ProcessingEquipmentService service = service();

        ProcessingEquipmentLease cooking = service.tryAcquire(
            SIGN, ProcessingEquipmentCapability.FERMENTATION, ProcessingClient.COOKING, "cooking:soy_sauce"
        );
        ProcessingEquipmentLease brewery = service.tryAcquire(
            BARREL, ProcessingEquipmentCapability.FERMENTATION, ProcessingClient.BREWERY, "brewery:fermentation"
        );

        assertNotNull(cooking);
        assertNull(brewery);
        assertSame(cooking, service.tryAcquire(
            SIGN, ProcessingEquipmentCapability.FERMENTATION, ProcessingClient.COOKING, "cooking:soy_sauce"
        ));
        assertEquals(cooking, service.leaseAt(BARREL));
        assertTrue(service.releaseAt(SIGN, ProcessingClient.COOKING, "cooking:soy_sauce"));
        assertNull(service.leaseAt(BARREL));
    }

    @Test
    void releasingOneClientDoesNotReleaseAnotherClientLease() {
        ProcessingEquipmentService service = service();
        ProcessingEquipmentLease lease = service.tryAcquire(
            BARREL, ProcessingEquipmentCapability.FERMENTATION, ProcessingClient.BREWERY, "brewery:fermentation"
        );

        assertNotNull(lease);
        assertFalse(service.releaseAt(BARREL, ProcessingClient.COOKING, "cooking:soy_sauce"));
        assertEquals(1, service.activeLeases().size());
        service.releaseAll(ProcessingClient.BREWERY);
        assertTrue(service.activeLeases().isEmpty());
    }

    @Test
    void capabilityLookupContinuesAcrossProviders() {
        ProcessingEquipmentService service = new ProcessingEquipmentService();
        ProcessingEquipment nonFermentation = new ProcessingEquipment(
            "other:equipment",
            SIGN,
            Set.of(SIGN),
            Set.of(ProcessingEquipmentCapability.MIXING)
        );
        ProcessingEquipment fermentation = new ProcessingEquipment(
            "brewery:fermentation_barrel",
            BARREL,
            Set.of(BARREL, SIGN),
            Set.of(ProcessingEquipmentCapability.FERMENTATION)
        );
        service.registerProvider("other", location -> nonFermentation.contains(location) ? nonFermentation : null);
        service.registerProvider("brewery", location -> fermentation.contains(location) ? fermentation : null);

        assertEquals("brewery:fermentation_barrel",
            service.findAt(SIGN, ProcessingEquipmentCapability.FERMENTATION).getId());
    }

    private static ProcessingEquipmentService service() {
        ProcessingEquipmentService service = new ProcessingEquipmentService();
        ProcessingEquipment equipment = new ProcessingEquipment(
            "brewery:fermentation_barrel",
            BARREL,
            Set.of(BARREL, SIGN),
            Set.of(ProcessingEquipmentCapability.FERMENTATION)
        );
        service.registerProvider("brewery", location -> equipment.contains(location) ? equipment : null);
        return service;
    }
}
