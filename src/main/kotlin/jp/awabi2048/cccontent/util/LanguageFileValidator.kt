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
    fun validateAll(plugin: JavaPlugin) {
        val errors = mutableListOf<String>()

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
                        validateYaml(reader.readText(), "resource:$resourcePath")
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
                    validateYaml(file.readText(Charsets.UTF_8), "file:${file.absolutePath}")
                } catch (e: Exception) {
                    errors += "file:${file.absolutePath} -> ${e.message}"
                }
            }
        }

        if (errors.isNotEmpty()) {
            val detail = errors.joinToString("\n") { "- $it" }
            throw IllegalStateException("言語ファイル検証に失敗しました:\n$detail")
        }
    }

    private fun validateYaml(content: String, source: String) {
        val options = LoaderOptions().apply {
            isAllowDuplicateKeys = false
            maxAliasesForCollections = 50
        }
        val yaml = Yaml(SafeConstructor(options))
        val loaded = yaml.load<Any?>(content)
        if (loaded !is Map<*, *>) {
            throw IllegalStateException("YAMLのルートがMapではありません: $source")
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
