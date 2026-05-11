package jp.awabi2048.cccontent;

import jp.awabi2048.cccontent.localization.ContentLanguageKeyRequirements;
import jp.awabi2048.cccontent.testsupport.LanguageResourceValidator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

class LanguageResourceValidationTest {
    @Test
    void languageResourcesStayComplete() throws IOException {
        LanguageResourceValidator.Result result = LanguageResourceValidator.validate(
            Path.of("src/main/resources/lang"),
            ContentLanguageKeyRequirements.requiredKeys(Path.of("src/main/resources"))
        );

        if (result.hasErrors()) {
            fail("[lang validation] " + result.errors().size() + " error(s)\n\n" + String.join("\n", result.errors()));
        }
    }

    @Test
    void literalLanguageKeysReferencedByCodeExistInBundledResources() throws IOException {
        Pattern literalKeyCall = Pattern.compile(
            "(?:ArenaI18n\\.(?:text|stringList)|CustomItemI18n\\.(?:text|list)|messageProvider\\.getMessage(?:List)?|languageLoader\\.get(?:Message|RawMessage|StringList)|getI18nString(?:List)?)\\([^\\n]*?\"([a-z0-9_]+(?:\\.[a-z0-9_]+)+)\""
        );
        List<String> errors = new java.util.ArrayList<>();

        try (Stream<Path> files = Files.walk(Path.of("src/main/kotlin"))) {
            for (Path file : files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".kt")).toList()) {
                String content = Files.readString(file);
                var matcher = literalKeyCall.matcher(content);
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (key.startsWith("block.minecraft.") || key.startsWith("item.minecraft.")) {
                        continue;
                    }
                    if (!LanguageResourceValidator.hasKey(Path.of("src/main/resources/lang"), key)) {
                        errors.add("[lang reference validation] missing key\n"
                            + "  file: " + file + "\n"
                            + "  key: " + key);
                    }
                }
            }
        }

        if (!errors.isEmpty()) {
            fail("[lang reference validation] " + errors.size() + " error(s)\n\n" + String.join("\n", errors));
        }
    }
}
