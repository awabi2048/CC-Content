package jp.awabi2048.cccontent.features.brewery.garden;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BreweryGardenStoreTest {
    @TempDir
    Path tempDirectory;

    @Test
    void plantTypeStageAndAbsoluteTimeSurviveReload() {
        File file = tempDirectory.resolve("garden.yml").toFile();
        GardenLocation location = new GardenLocation(UUID.randomUUID(), 10, 64, -20);
        GardenPlantState state = new GardenPlantState("grape", 1, 1_000, 5_000);

        new GardenStore(file).put(location, state);
        GardenPlantState restored = new GardenStore(file).get(location);

        assertEquals(state, restored);
    }

    @Test
    void missingPlantIdIsRejectedInsteadOfFallingBack() throws Exception {
        File file = tempDirectory.resolve("invalid.yml").toFile();
        UUID worldId = UUID.randomUUID();
        Files.writeString(file.toPath(), """
                schema_version: 1
                plants:
                  %s,0,64,0:
                    plant_stage: 0
                    planted_at: 1
                    next_growth_at: 2
                """.formatted(worldId));

        assertThrows(IllegalStateException.class, () -> new GardenStore(file));
    }
}
