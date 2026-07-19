package jp.awabi2048.cccontent.features.resourcecollection;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceCollectionSettingsTest {
    @Test
    void bundledConfigurationContainsEveryOperationSwitch() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
            new File("src/main/resources/config/resource_collection/config.yml")
        );

        assertTrue(yaml.getInt("config_version") == 6);
        assertTrue(yaml.getInt("chisel.target_count") == 3);
        assertTrue(yaml.getLong("chisel.target_timeout_ticks") == 50L);
        assertTrue(yaml.getInt("chisel.particle_count") == 5);
        for (ResourceOperation operation : ResourceOperation.values()) {
            assertTrue(yaml.get(operation.getConfigPath()) instanceof Boolean, operation.getConfigPath());
        }
    }

    @Test
    void professionSwitchDisablesEveryOperationInThatProfession() {
        Map<ResourceCollectionKind, Boolean> professions = enabledProfessions();
        professions.put(ResourceCollectionKind.FOREST, false);
        ResourceCollectionSettings settings = settings(professions, enabledOperations());

        assertFalse(settings.isOperationEnabled(ResourceOperation.LUMBERJACK_BATCH));
        assertFalse(settings.isOperationEnabled(ResourceOperation.LUMBERJACK_BARK));
        assertTrue(settings.isOperationEnabled(ResourceOperation.MINER_CHISEL));
        assertTrue(settings.isOperationEnabled(ResourceOperation.FARMER_AREA_HARVEST));
    }

    @Test
    void individualOperationSwitchDoesNotDisableAdjacentOperations() {
        Map<ResourceOperation, Boolean> operations = enabledOperations();
        operations.put(ResourceOperation.FARMER_SURFACE_GATHERING, false);
        ResourceCollectionSettings settings = settings(enabledProfessions(), operations);

        assertFalse(settings.isOperationEnabled(ResourceOperation.FARMER_SURFACE_GATHERING));
        assertTrue(settings.isOperationEnabled(ResourceOperation.FARMER_WILD_GATHERING));
        assertTrue(settings.isOperationEnabled(ResourceOperation.FARMER_AREA_HARVEST));
    }

    private static ResourceCollectionSettings settings(
        Map<ResourceCollectionKind, Boolean> professions,
        Map<ResourceOperation, Boolean> operations
    ) {
        return new ResourceCollectionSettings(
            true,
            new ChiselSettings(3, 50L, 5),
            professions,
            enabledProfessions(),
            operations
        );
    }

    private static Map<ResourceCollectionKind, Boolean> enabledProfessions() {
        Map<ResourceCollectionKind, Boolean> values = new EnumMap<>(ResourceCollectionKind.class);
        for (ResourceCollectionKind kind : ResourceCollectionKind.values()) values.put(kind, true);
        return values;
    }

    private static Map<ResourceOperation, Boolean> enabledOperations() {
        Map<ResourceOperation, Boolean> values = new EnumMap<>(ResourceOperation.class);
        for (ResourceOperation operation : ResourceOperation.values()) values.put(operation, true);
        return values;
    }
}
