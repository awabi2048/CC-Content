package jp.awabi2048.cccontent.features.playerdata

import org.bukkit.configuration.InvalidConfigurationException
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * playerdata配下のプレイヤー単位YAML（`<uuid>.yml`）への安全な読み書き窓口。
 *
 * 背景として、同ファイルは `rank.*`（YamlRankStorage）と `sukima_dungeon.*`（PlayerDataManager）が
 * 共有しているにもかかわらず、従来は各所有者が独立に load-modify-save していた。
 * さらに保存が直接上書きだったため、書き込み中断で先頭NUL（0x0）等の破損が残ると、
 * 次回読み込みで SnakeYAML の `special characters are not allowed` が大量に出力され、
 * その後の保存が他節を消した状態で上書きする問題があった。
 *
 * 本ユーティリティは以下の責務だけを持ち、保存形式・キー体系は変えない。
 * - 同一ファイルへの並行アクセスをファイル単位ロックで直列化する
 * - NUL混入やYAMLパース失敗を検出し、破損原本を `corrupted/` へ退避してデフォルト復帰する
 * - 保存は同一ディレクトリの一時ファイル経由＋置換で行い、途中クラッシュで既存ファイルを壊さない
 */
object PlayerDataFiles {
    private val logger = Logger.getLogger("CC-Content")

    /** ファイル単位の直列化ロック。絶対パスをキーに使い、所有者間で共有される。 */
    private val locks = ConcurrentHashMap<String, Any>()

    /** 同一内容の破損ファイルを何度も退避しないための指紋（パス→指紋）。 */
    private val quarantinedFingerprints = ConcurrentHashMap<String, String>()

    private fun lockFor(file: File): Any =
        locks.computeIfAbsent(file.absoluteFile.normalize().absolutePath) { Any() }

    /**
     * 破損時は空コンフィグを返し、原本を `corrupted/` へ退避する。
     * 存在しないファイルや空ファイルは新規扱いで空コンフィグを返す。
     */
    fun load(file: File): YamlConfiguration {
        if (!file.exists()) return YamlConfiguration()
        synchronized(lockFor(file)) {
            return loadLocked(file)
        }
    }

    /**
     * 既存内容を保持したまま [mutate] を適用し、原子的に保存する。
     * 破損ファイルは先に退避されるため、他節を道連れにした黙示的上書きは起きない。
     */
    fun update(file: File, mutate: (YamlConfiguration) -> Unit) {
        file.parentFile?.mkdirs()
        synchronized(lockFor(file)) {
            val config = loadLocked(file)
            mutate(config)
            saveLocked(file, config)
        }
    }

    private fun loadLocked(file: File): YamlConfiguration {
        if (!file.exists()) return YamlConfiguration()
        val bytes = try {
            Files.readAllBytes(file.toPath())
        } catch (error: Exception) {
            logger.severe("[CC-Content] プレイヤーデータを読めませんでした: ${file.name} (${error.message})。デフォルト値で復帰します。")
            return YamlConfiguration()
        }
        if (bytes.isEmpty()) return YamlConfiguration()

        // 実測された破損（先頭NULによる ReaderException）を先に検出する。
        // SnakeYAMLの許容範囲外である0x00が1バイトでも混じればYAMLとして扱わない。
        if (bytes.any { it == 0.toByte() }) {
            quarantineOnce(file, fingerprint(bytes), "NUL byte detected")
            return YamlConfiguration()
        }

        val text = try {
            decodeStrictUtf8(bytes)
        } catch (error: CharacterCodingException) {
            quarantineOnce(file, fingerprint(bytes), "invalid UTF-8")
            return YamlConfiguration()
        }

        return try {
            val config = YamlConfiguration()
            config.loadFromString(stripBom(text))
            config
        } catch (error: InvalidConfigurationException) {
            quarantineOnce(file, fingerprint(bytes), error.message ?: "invalid YAML")
            YamlConfiguration()
        }
    }

    private fun saveLocked(file: File, config: YamlConfiguration) {
        val directory = file.parentFile ?: throw IllegalStateException("保存先ディレクトリを解決できません: $file")
        directory.mkdirs()
        val temporary = Files.createTempFile(directory.toPath(), file.name + ".", ".tmp")
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
        } catch (error: Exception) {
            logger.severe("[CC-Content] プレイヤーデータを保存できませんでした: ${file.name} (${error.message})")
            throw error
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun decodeStrictUtf8(bytes: ByteArray): String {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    }

    private fun stripBom(text: String): String =
        if (text.isNotEmpty() && text[0] == '\uFEFF') text.substring(1) else text

    private fun fingerprint(bytes: ByteArray): String =
        "${bytes.size}:${bytes.contentHashCode()}"

    private fun quarantineOnce(file: File, fingerprint: String, reason: String) {
        val key = file.absoluteFile.normalize().absolutePath
        val previous = quarantinedFingerprints.putIfAbsent(key, fingerprint)
        if (previous == fingerprint) return
        // ファイル内容が変わっていたら再度退避できるよう指紋を更新する。
        quarantinedFingerprints[key] = fingerprint
        try {
            val corruptedDirectory = File(file.parentFile, "corrupted").apply { mkdirs() }
            val destination = File(
                corruptedDirectory,
                "${file.nameWithoutExtension}.corrupted-${System.currentTimeMillis()}.yml"
            )
            Files.copy(file.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            logger.warning(
                "[CC-Content] 破損したプレイヤーデータを隔離しました: " +
                    "${file.name} -> corrupted/${destination.name} ($reason)。デフォルト値で復帰します。"
            )
        } catch (error: Exception) {
            logger.severe("[CC-Content] 破損プレイヤーデータの隔離に失敗しました: ${file.name} (${error.message})")
        }
    }
}
