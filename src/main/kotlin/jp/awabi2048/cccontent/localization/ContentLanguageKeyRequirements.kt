package jp.awabi2048.cccontent.localization

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.nio.file.Files
import java.nio.file.Path

object ContentLanguageKeyRequirements {
    @JvmStatic
    fun requiredKeys(resourcesRoot: Path): List<LanguageKeyRequirement> {
        return arenaThemeKeys(resourcesRoot)
    }

    private fun arenaThemeKeys(resourcesRoot: Path): List<LanguageKeyRequirement> {
        val themeDir = resourcesRoot.resolve("config/arena/themes")
        if (!Files.isDirectory(themeDir)) {
            return emptyList()
        }

        return Files.list(themeDir).use { files ->
            files
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".yml") || it.fileName.toString().endsWith(".yaml") }
                .map { file -> themeId(file) ?: file.fileName.toString().substringBeforeLast('.') }
                .sorted()
                .map { id ->
                    LanguageKeyRequirement(
                        sourceId = "CC-Content:arena",
                        key = "arena.theme.$id.name",
                        reason = "config/arena/themes defines arena theme id=$id"
                    )
                }
                .toList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun themeId(file: Path): String? {
        val options = LoaderOptions().apply {
            isAllowDuplicateKeys = false
            maxAliasesForCollections = 50
        }
        val loaded = Yaml(SafeConstructor(options)).load<Any?>(Files.readString(file)) as? Map<String, Any?>
        return loaded?.get("id")?.toString()?.takeIf { it.isNotBlank() }
    }
}
