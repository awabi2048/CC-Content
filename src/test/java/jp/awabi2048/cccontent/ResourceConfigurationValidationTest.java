package jp.awabi2048.cccontent;

import jp.awabi2048.cccontent.config.ResourceConfigurationValidator;
import jp.awabi2048.cccontent.features.npc.shop.OageShrineShopConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

class ResourceConfigurationValidationTest {
    @Test
    void bundledConfigurationResourcesDoNotContainStartupBlockingErrors() throws IOException {
        List<String> errors = new ArrayList<>();
        errors.addAll(ResourceConfigurationValidator.validateConfigDirectory(Path.of("src/main/resources/config")));
        validateOageShrineShop(errors);

        if (!errors.isEmpty()) {
            fail("[resource config validation] " + errors.size() + " error(s)\n\n" + String.join("\n", errors));
        }
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
