package jp.awabi2048.cccontent;

import com.awabi2048.ccsystem.api.localization.LocalizationCatalogContract;
import com.awabi2048.ccsystem.api.localization.LocalizationKey;
import jp.awabi2048.cccontent.localization.ContentLanguageKeyRequirements;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

class LanguageResourceValidationTest {
    @Test
    void requiredAndLiteralLanguageKeysExistInEmbeddedCatalogWithExpectedType() throws IOException {
        List<String> errors = new ArrayList<>();
        for (var requirement : ContentLanguageKeyRequirements.requiredKeys(Path.of("src/main/resources"))) {
            requireKey(errors, Path.of("src/main/resources"), requirement.getKey(), null);
        }

        Pattern literalKeyCall = Pattern.compile(
            "(ArenaI18n\\.(text|stringList)|CustomItemI18n\\.(text|list)|messageProvider\\.getMessage(List)?|"
                + "languageLoader\\.get(Message|RawMessage|StringList)|getI18nString(List)?)"
                + "\\([^\\n]*?\"([a-z0-9_]+(?:\\.[a-z0-9_]+)+)\""
        );
        try (Stream<Path> files = Files.walk(Path.of("src/main/kotlin"))) {
            for (Path file : files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".kt")).toList()) {
                var matcher = literalKeyCall.matcher(Files.readString(file));
                while (matcher.find()) {
                    String key = matcher.group(7);
                    if (key.startsWith("block.minecraft.") || key.startsWith("item.minecraft.")) continue;
                    errors.add("fixed key must use generated LocalizationKey\n  file: " + file + "\n  key: " + key);
                }
            }
        }
        if (!errors.isEmpty()) {
            fail("[embedded localization validation] " + errors.size() + " error(s)\n\n" + String.join("\n", errors));
        }
    }

    private static void requireKey(List<String> errors, Path file, String key, LocalizationKey.ValueType expected) {
        if (!LocalizationCatalogContract.INSTANCE.contains(key)) {
            errors.add("missing key\n  file: " + file + "\n  key: " + key);
        } else if (expected != null && LocalizationCatalogContract.INSTANCE.valueType(key) != expected) {
            errors.add("value type mismatch\n  file: " + file + "\n  key: " + key
                + "\n  expected: " + expected + "\n  actual: " + LocalizationCatalogContract.INSTANCE.valueType(key));
        }
    }
}
