package jp.awabi2048.cccontent;

import com.awabi2048.ccsystem.CCSystem;
import jp.awabi2048.cccontent.testsupport.LanguageResourceValidator;
import jp.awabi2048.cccontent.localization.ContentLanguageKeyRequirements;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

class LanguageResourceValidationTest {
    @Test
    void languageResourcesStayComplete() throws IOException {
        var result = withCcSystemLanguageRoot(langRoot -> LanguageResourceValidator.validate(
            langRoot, ContentLanguageKeyRequirements.requiredKeys(Path.of("src/main/resources"))
        ));
        if (result.hasErrors()) {
            fail("[lang validation] " + result.errors().size() + " error(s)\n\n"
                + String.join("\n", result.errors()));
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
                    if (!withCcSystemLanguageRoot(langRoot -> LanguageResourceValidator.hasKey(langRoot, key))) {
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

    /** 独立リポジトリの配置名に依存せず、宣言済みCC-System成果物そのものを検証します。 */
    private static <T> T withCcSystemLanguageRoot(LanguageRootOperation<T> operation) throws IOException {
        Path artifact;
        try {
            artifact = Path.of(CCSystem.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException exception) {
            throw new IOException("CC-System成果物の場所を解決できません", exception);
        }
        if (Files.isDirectory(artifact)) {
            return operation.apply(artifact.resolve("lang"));
        }
        try (FileSystem jar = FileSystems.newFileSystem(artifact, Map.of())) {
            return operation.apply(jar.getPath("/lang"));
        }
    }

    @FunctionalInterface
    private interface LanguageRootOperation<T> {
        T apply(Path languageRoot) throws IOException;
    }
}
