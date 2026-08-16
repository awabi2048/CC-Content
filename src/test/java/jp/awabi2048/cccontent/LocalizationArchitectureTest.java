package jp.awabi2048.cccontent;

import com.awabi2048.ccsystem.api.localization.LocalizationCatalogContract;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationArchitectureTest {
    @Test
    void consumerCodeUsesOnlyTypedLocalizationApi() throws IOException {
        List<String> errors = new ArrayList<>();
        try (var files = Files.walk(Path.of("src/main/kotlin"))) {
            for (Path file : files.filter(Files::isRegularFile).filter(it -> it.toString().endsWith(".kt")).toList()) {
                String source = Files.readString(file);
                for (String forbidden : List.of(
                    "getI18nString(", "getI18nStringList(", "hasI18nKey(",
                    "LocalizationKey.text(", "LocalizationKey.textList("
                )) {
                    if (source.contains(forbidden)) errors.add(file + ": forbidden localization API: " + forbidden);
                }
                if (source.contains("LocalizationCatalogContract") && !file.endsWith("ContentLocalizationKeys.kt")) {
                    errors.add(file + ": dynamic catalog resolution must be centralized");
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join("\n", errors));
    }

    @Test
    void bundledSeasonalPlantKeysResolveAsText() {
        var yaml = YamlConfiguration.loadConfiguration(
            Path.of("src/main/resources/config/resource_collection/seasonal_plants.yml").toFile()
        );
        List<String> errors = new ArrayList<>();
        for (var definition : yaml.getMapList("definitions")) {
            for (String field : List.of("use_name_key", "vegetation_group_name_key")) {
                String key = String.valueOf(definition.get(field));
                try {
                    LocalizationCatalogContract.resolveText(key);
                } catch (RuntimeException error) {
                    errors.add(field + "=" + key + ": " + error.getMessage());
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join("\n", errors));
    }
}
