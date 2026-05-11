package jp.awabi2048.cccontent.testsupport;

import jp.awabi2048.cccontent.localization.LanguageKeyRequirement;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LanguageResourceValidator {
    private static final String BASE_LOCALE = "ja_jp";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[A-Za-z0-9_]+}");

    private LanguageResourceValidator() {
    }

    public static Result validate(Path langRoot, List<LanguageKeyRequirement> requiredKeys) throws IOException {
        Map<String, Map<String, Map<String, Object>>> locales = loadLocales(langRoot);
        List<String> errors = new ArrayList<>();

        if (locales.isEmpty()) {
            errors.add("[lang validation] no locale directories found: " + langRoot);
        }
        if (!locales.containsKey(BASE_LOCALE)) {
            errors.add("[lang validation] missing base locale: " + BASE_LOCALE);
        }

        Map<String, Map<String, Object>> baseFiles = locales.getOrDefault(BASE_LOCALE, Map.of());
        Set<String> allFiles = new TreeSet<>();
        locales.values().forEach(files -> allFiles.addAll(files.keySet()));

        for (String locale : new TreeSet<>(locales.keySet())) {
            Map<String, Map<String, Object>> files = locales.get(locale);
            for (String fileName : allFiles) {
                Map<String, Object> base = baseFiles.get(fileName);
                Map<String, Object> actual = files.get(fileName);
                if (base == null) {
                    errors.add(format("extra file", locale, fileName, "<file>", "base locale does not contain this file"));
                } else if (actual == null) {
                    errors.add(format("missing file", locale, fileName, "<file>", "base=" + BASE_LOCALE + "/" + fileName));
                } else {
                    compareNode(errors, locale, fileName, "", base, actual);
                    findExtraKeys(errors, locale, fileName, "", base, actual);
                }
            }
        }

        for (LanguageKeyRequirement requirement : requiredKeys) {
            for (String locale : new TreeSet<>(locales.keySet())) {
                if (!hasKey(locales.get(locale), requirement.getKey())) {
                    errors.add(format("missing generated key", locale, "<merged>", requirement.getKey(), requirement.getReason()));
                }
            }
        }

        return new Result(errors);
    }

    public static boolean hasKey(Path langRoot, String dottedKey) throws IOException {
        Map<String, Map<String, Map<String, Object>>> locales = loadLocales(langRoot);
        for (Map<String, Map<String, Object>> files : locales.values()) {
            if (hasKey(files, dottedKey)) {
                return true;
            }
        }
        return false;
    }

    public record Result(List<String> errors) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    private static Map<String, Map<String, Map<String, Object>>> loadLocales(Path langRoot) throws IOException {
        Map<String, Map<String, Map<String, Object>>> locales = new LinkedHashMap<>();
        if (!Files.isDirectory(langRoot)) {
            return locales;
        }
        try (var localeDirs = Files.list(langRoot)) {
            for (Path localeDir : localeDirs.filter(Files::isDirectory).sorted().toList()) {
                Map<String, Map<String, Object>> files = new LinkedHashMap<>();
                try (var ymlFiles = Files.list(localeDir)) {
                    for (Path file : ymlFiles.filter(Files::isRegularFile).filter(LanguageResourceValidator::isYaml).sorted().toList()) {
                        files.put(file.getFileName().toString(), readYaml(file));
                    }
                }
                locales.put(localeDir.getFileName().toString().toLowerCase(), files);
            }
        }
        return locales;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readYaml(Path file) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(50);
        Object loaded = new Yaml(new SafeConstructor(options)).load(Files.readString(file));
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalStateException("[lang validation] YAML root must be a map: " + file);
        }
        return (Map<String, Object>) map;
    }

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static void compareNode(List<String> errors, String locale, String fileName, String path, Object expected, Object actual) {
        if (!nodeType(expected).equals(nodeType(actual))) {
            errors.add(format("type mismatch", locale, fileName, displayPath(path), "expected=" + nodeType(expected) + " actual=" + nodeType(actual)));
            return;
        }
        if (expected instanceof Map<?, ?> expectedMap && actual instanceof Map<?, ?> actualMap) {
            for (Map.Entry<?, ?> entry : expectedMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String childPath = path.isEmpty() ? key : path + "." + key;
                if (!actualMap.containsKey(entry.getKey())) {
                    errors.add(format("missing key", locale, fileName, childPath, "base=" + BASE_LOCALE + "/" + fileName));
                } else {
                    compareNode(errors, locale, fileName, childPath, entry.getValue(), actualMap.get(entry.getKey()));
                }
            }
        } else if (expected instanceof String expectedString && actual instanceof String actualString) {
            Set<String> expectedPlaceholders = placeholders(expectedString);
            Set<String> actualPlaceholders = placeholders(actualString);
            if (!expectedPlaceholders.equals(actualPlaceholders)) {
                errors.add(format("placeholder mismatch", locale, fileName, displayPath(path), "expected=" + expectedPlaceholders + " actual=" + actualPlaceholders));
            }
        }
    }

    private static void findExtraKeys(List<String> errors, String locale, String fileName, String path, Object expected, Object actual) {
        if (expected instanceof Map<?, ?> expectedMap && actual instanceof Map<?, ?> actualMap) {
            for (Map.Entry<?, ?> entry : actualMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String childPath = path.isEmpty() ? key : path + "." + key;
                if (!expectedMap.containsKey(entry.getKey())) {
                    errors.add(format("extra key", locale, fileName, childPath, "base=" + BASE_LOCALE + "/" + fileName));
                } else {
                    findExtraKeys(errors, locale, fileName, childPath, expectedMap.get(entry.getKey()), entry.getValue());
                }
            }
        }
    }

    private static boolean hasKey(Map<String, Map<String, Object>> files, String dottedKey) {
        for (Map<String, Object> root : files.values()) {
            if (resolve(root, dottedKey.split("\\.")) != null) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Object resolve(Map<String, Object> root, String[] parts) {
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(part)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
        }
        return current;
    }

    private static String nodeType(Object value) {
        if (value instanceof Map<?, ?>) return "Map";
        if (value instanceof List<?>) return "List";
        if (value == null) return "Null";
        return "Scalar";
    }

    private static Set<String> placeholders(String value) {
        Set<String> placeholders = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            placeholders.add(matcher.group());
        }
        return placeholders;
    }

    private static String displayPath(String path) {
        return path.isEmpty() ? "<root>" : path;
    }

    private static String format(String type, String locale, String file, String key, String detail) {
        return "[lang validation] " + type + "\n"
            + "  locale: " + locale + "\n"
            + "  file: src/main/resources/lang/" + locale + "/" + file + "\n"
            + "  key: " + key + "\n"
            + "  detail: " + detail;
    }
}
