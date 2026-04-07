package jp.awabi2048.cccontent.util

import org.bukkit.plugin.java.JavaPlugin
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.jar.JarFile

object LanguageFileValidator {
    data class ValidationResult(
        val errors: List<String>
    ) {
        val hasErrors: Boolean
            get() = errors.isNotEmpty()

        fun throwIfInvalid() {
            if (errors.isEmpty()) {
                return
            }
            val detail = errors.joinToString("\n") { "- $it" }
            throw IllegalStateException("言語ファイル検証に失敗しました:\n$detail")
        }
    }

    fun validateAll(plugin: JavaPlugin) {
        validateAndCollect(plugin).throwIfInvalid()
    }

    fun validateAndCollect(plugin: JavaPlugin): ValidationResult {
        val errors = mutableListOf<String>()
        val resourceMaps = mutableMapOf<String, Map<*, *>>()

        val resourceLangPaths = discoverResourceLangPaths(plugin)
        if (resourceLangPaths.isEmpty()) {
            errors += "lang リソースが見つかりません"
        }

        for (resourcePath in resourceLangPaths) {
            try {
                val resource = plugin.getResource(resourcePath)
                    ?: throw IllegalStateException("リソースを取得できません: $resourcePath")
                resource.use { input ->
                    InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                        val parsed = validateYaml(reader.readText(), "resource:$resourcePath")
                        resourceMaps[resourcePath] = parsed
                    }
                }
            } catch (e: Exception) {
                errors += "resource:$resourcePath -> ${e.message}"
            }
        }

        val langDir = File(plugin.dataFolder, "lang")
        if (langDir.exists()) {
            val files = langDir.listFiles { file ->
                file.isFile && (file.name.endsWith(".yml", ignoreCase = true) || file.name.endsWith(".yaml", ignoreCase = true))
            }?.sortedBy { it.name.lowercase() }.orEmpty()

            for (file in files) {
                try {
                    val parsed = validateYaml(file.readText(Charsets.UTF_8), "file:${file.absolutePath}")
                    val resourcePath = "lang/${file.name}"
                    val resourceMap = resourceMaps[resourcePath]
                    if (resourceMap != null) {
                        val structuralErrors = mutableListOf<String>()
                        validateStructure(
                            expected = resourceMap,
                            actual = parsed,
                            path = "",
                            errors = structuralErrors
                        )
                        if (structuralErrors.isNotEmpty()) {
                            val detail = structuralErrors.joinToString("\n") { "  - $it" }
                            errors += "file:${file.absolutePath} -> リソースとの整合性エラー:\n$detail"
                        }
                    }
                } catch (e: Exception) {
                    errors += "file:${file.absolutePath} -> ${e.message}"
                }
            }
        }

        return ValidationResult(errors)
    }

    private fun validateYaml(content: String, source: String): Map<*, *> {
        val options = LoaderOptions().apply {
            isAllowDuplicateKeys = false
            maxAliasesForCollections = 50
        }
        val yaml = Yaml(SafeConstructor(options))
        val loaded = yaml.load<Any?>(content)
        if (loaded !is Map<*, *>) {
            throw IllegalStateException("YAMLのルートがMapではありません: $source")
        }
        return loaded
    }

    private fun validateStructure(
        expected: Any?,
        actual: Any?,
        path: String,
        errors: MutableList<String>
    ) {
        val expectedType = nodeType(expected)
        val actualType = nodeType(actual)
        val displayPath = if (path.isBlank()) "<root>" else path

        if (expectedType != actualType) {
            errors += "$displayPath: 型不一致 expected=$expectedType actual=$actualType"
            return
        }

        if (expected is Map<*, *> && actual is Map<*, *>) {
            for ((key, expectedValue) in expected) {
                if (key !is String) continue
                if (!actual.containsKey(key)) {
                    val missingPath = if (path.isBlank()) key else "$path.$key"
                    errors += "$missingPath: キー不足"
                    continue
                }
                val childPath = if (path.isBlank()) key else "$path.$key"
                validateStructure(expectedValue, actual[key], childPath, errors)
            }
        }
    }

    private fun nodeType(value: Any?): String {
        return when (value) {
            is Map<*, *> -> "Map"
            is List<*> -> "List"
            null -> "Null"
            else -> "Scalar"
        }
    }

    private fun discoverResourceLangPaths(plugin: JavaPlugin): List<String> {
        val fallback = listOf("lang/ja_jp.yml", "lang/en_us.yml").filter { plugin.getResource(it) != null }
        val codeSource = runCatching { plugin.javaClass.protectionDomain.codeSource.location.toURI() }.getOrNull()
            ?: return fallback
        val file = File(codeSource)
        if (!file.isFile || !file.name.endsWith(".jar", ignoreCase = true)) {
            return fallback
        }

        return runCatching {
            JarFile(file).use { jar ->
                jar.entries().asSequence()
                    .filter { !it.isDirectory }
                    .map { it.name }
                    .filter { it.startsWith("lang/") }
                    .filter { it.endsWith(".yml") || it.endsWith(".yaml") }
                    .sorted()
                    .toList()
            }
        }.getOrElse { fallback }
    }
}
