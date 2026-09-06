package jp.awabi2048.cccontent.features.arena

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * YAML永続化のクラッシュ安全性のための原子保存ユーティリティ。
 * 同一ディレクトリ内の一時ファイルへ書き出してから移動するため、
 * 書き込み途中のクラッシュで既存ファイルが半端な内容になることを防ぐ。
 * 保存する内容・形式は呼び出し側が組み立てたものと同一であり、意味変更はない。
 */
internal object ArenaYamlFiles {
    fun saveAtomically(file: File, writer: YamlConfiguration.() -> Unit) {
        val directory = file.parentFile ?: throw IllegalStateException("保存先ディレクトリを解決できません: $file")
        directory.mkdirs()
        val config = YamlConfiguration()
        config.writer()
        val temporary = Files.createTempFile(directory.toPath(), file.name, ".tmp")
        try {
            config.save(temporary.toFile())
            try {
                Files.move(
                    temporary,
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (error: AtomicMoveNotSupportedException) {
                Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
