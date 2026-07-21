package jp.awabi2048.cccontent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class UnifiedContentInitializationContractTest {
    @Test
    void resourceItemsAreInitializedBeforeRecipesThatConsumeThem() throws Exception {
        String source = Files.readString(
            Path.of("src/main/kotlin/jp/awabi2048/cccontent/CCContent.kt"),
            StandardCharsets.UTF_8
        );

        int resources = source.indexOf("initializeFeatureIfEnabled(\"Resource Collection\"");
        int brewery = source.indexOf("initializeFeatureIfEnabled(\"Brewery\"");
        int cooking = source.indexOf("initializeFeatureIfEnabled(\"Cooking\"");
        assertTrue(resources >= 0 && resources < brewery && resources < cooking);
    }

    @Test
    void gatheredResourceDefinitionsReplaceOldBundledData() throws Exception {
        String source = Files.readString(
            Path.of("src/main/kotlin/jp/awabi2048/cccontent/CCContent.kt"),
            StandardCharsets.UTF_8
        );
        assertTrue(source.contains("\"config/resource_collection/seasonal_plants.yml\""));
        assertTrue(source.contains("\"config/resource_collection/forest_products.yml\""));

        assertVersionedDefinition("seasonal_plants.yml");
        assertVersionedDefinition("forest_products.yml");
    }

    private static void assertVersionedDefinition(String name) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
            Path.of("src/main/resources/config/resource_collection", name).toFile()
        );
        assertTrue(config.getInt("schema_version") == 2);
        assertTrue(config.getInt("config_version") == 2);
    }
}
