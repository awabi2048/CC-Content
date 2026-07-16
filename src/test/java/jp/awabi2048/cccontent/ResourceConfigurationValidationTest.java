package jp.awabi2048.cccontent;

import jp.awabi2048.cccontent.config.ResourceConfigurationValidator;
import jp.awabi2048.cccontent.features.npc.shop.OageShrineShopConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceConfigurationValidationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void bundledConfigurationResourcesDoNotContainStartupBlockingErrors() throws IOException {
        List<String> errors = new ArrayList<>();
        errors.addAll(ResourceConfigurationValidator.validateConfigDirectory(Path.of("src/main/resources/config")));
        validateOageShrineShop(errors);

        if (!errors.isEmpty()) {
            fail("[resource config validation] " + errors.size() + " error(s)\n\n" + String.join("\n", errors));
        }
    }

    @Test
    void unknownContentFeatureKeyIsRejected() throws IOException {
        Path source = Path.of("src/main/resources/config");
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = temporaryDirectory.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target);
                }
            }
        }
        Path core = temporaryDirectory.resolve("core.yml");
        String coreYaml = Files.readString(core, StandardCharsets.UTF_8);
        Files.writeString(
                core,
                coreYaml.replaceFirst("content_enabled:\\R", "content_enabled:\n  unknown_feature: true\n"),
                StandardCharsets.UTF_8
        );

        List<String> errors = ResourceConfigurationValidator.validateConfigDirectory(temporaryDirectory);
        assertTrue(errors.stream().anyMatch(error -> error.contains("content_enabled.unknown_feature")), errors.toString());
    }

    @Test
    void partyOfflineGraceSecondsIsRequiredPositiveInteger() throws IOException {
        Path source = Path.of("src/main/resources/config");
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = temporaryDirectory.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) Files.createDirectories(target);
                else Files.copy(path, target);
            }
        }
        Path party = temporaryDirectory.resolve("party/config.yml");
        Files.writeString(party, "party:\n  default_capacity: 6\n  invite_expiration_seconds: 60\n", StandardCharsets.UTF_8);
        List<String> missing = ResourceConfigurationValidator.validateConfigDirectory(temporaryDirectory);
        assertTrue(missing.stream().anyMatch(error -> error.contains("party.offline_grace_seconds")), missing.toString());

        Files.writeString(party, "party:\n  default_capacity: 6\n  invite_expiration_seconds: 60\n  offline_grace_seconds: invalid\n", StandardCharsets.UTF_8);
        List<String> invalid = ResourceConfigurationValidator.validateConfigDirectory(temporaryDirectory);
        assertTrue(invalid.stream().anyMatch(error -> error.contains("party.offline_grace_seconds")), invalid.toString());
    }

    private static void validateOageShrineShop(List<String> errors) throws IOException {
        Path file = Path.of("src/main/resources/config/npc/oage_shrine.yml");
        for (String error : OageShrineShopConfig.validateConfigFile(file)) {
            errors.add(format("invalid oage shrine shop config", file, "config/npc/oage_shrine.yml", error));
        }
    }

    private static String format(String type, Path file, String key, String detail) {
        return "[resource config validation] " + type + "\n"
            + "  file: " + file + "\n"
            + "  key: " + key + "\n"
            + "  detail: " + detail;
    }
}
