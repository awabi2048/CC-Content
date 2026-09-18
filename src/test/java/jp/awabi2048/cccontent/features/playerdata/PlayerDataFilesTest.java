package jp.awabi2048.cccontent.features.playerdata;

import kotlin.Unit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 破損プレイヤーデータの隔離と共有ファイル保存の回帰テスト。
 * 実測された先頭NUL（0x0）破損を再現し、デフォルト復帰と他節保持を検証する。
 */
class PlayerDataFilesTest {
    @TempDir
    Path tempDirectory;

    @Test
    void nulPrefixedFileIsQuarantinedAndLoadsAsEmpty() throws Exception {
        File playerData = tempDirectory.resolve("playerdata").toFile();
        assertTrue(playerData.mkdirs());
        UUID uuid = UUID.randomUUID();
        File file = new File(playerData, uuid + ".yml");

        byte[] broken = new byte[]{0, 'r', 'a', 'n', 'k', ':', '\n'};
        Files.write(file.toPath(), broken);

        YamlConfiguration loaded = PlayerDataFiles.INSTANCE.load(file);
        assertFalse(loaded.contains("rank"));
        assertTrue(loaded.getKeys(false).isEmpty());

        File corrupted = new File(playerData, "corrupted");
        assertTrue(corrupted.isDirectory());
        try (Stream<Path> files = Files.list(corrupted.toPath())) {
            assertEquals(1, files.count());
        }

        // 同一内容の再読み込みで隔離ファイルが増殖しないこと。
        PlayerDataFiles.INSTANCE.load(file);
        try (Stream<Path> files = Files.list(corrupted.toPath())) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void updatePreservesUnrelatedSectionsAndLeavesNoTempFiles() throws Exception {
        File playerData = tempDirectory.resolve("playerdata").toFile();
        assertTrue(playerData.mkdirs());
        UUID uuid = UUID.randomUUID();
        File file = new File(playerData, uuid + ".yml");

        YamlConfiguration initial = new YamlConfiguration();
        initial.set("rank.tutorial.currentRank", "NEWBIE");
        initial.set("sukima_dungeon.lang", "en_us");
        initial.save(file);

        PlayerDataFiles.INSTANCE.update(file, config -> {
            config.set("rank.tutorial.currentRank", "ATTAINER");
            return Unit.INSTANCE;
        });

        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.load(file);
        assertEquals("ATTAINER", reloaded.getString("rank.tutorial.currentRank"));
        assertEquals("en_us", reloaded.getString("sukima_dungeon.lang"));

        PlayerDataFiles.INSTANCE.update(file, config -> {
            config.set("sukima_dungeon.lang", "ja_jp");
            return Unit.INSTANCE;
        });

        YamlConfiguration reloadedAgain = new YamlConfiguration();
        reloadedAgain.load(file);
        assertEquals("ATTAINER", reloadedAgain.getString("rank.tutorial.currentRank"));
        assertEquals("ja_jp", reloadedAgain.getString("sukima_dungeon.lang"));

        try (Stream<Path> files = Files.list(playerData.toPath())) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().contains(".tmp")));
        }
    }

    @Test
    void brokenFileRecoversToCleanStateOnNextUpdate() throws Exception {
        File playerData = tempDirectory.resolve("playerdata").toFile();
        assertTrue(playerData.mkdirs());
        UUID uuid = UUID.randomUUID();
        File file = new File(playerData, uuid + ".yml");
        Files.write(file.toPath(), "rank:\n  tutorial:\n".getBytes(StandardCharsets.UTF_8));
        // 先頭をNULで破壊する。
        byte[] bytes = Files.readAllBytes(file.toPath());
        bytes[0] = 0;
        Files.write(file.toPath(), bytes);

        PlayerDataFiles.INSTANCE.update(file, config -> {
            config.set("rank.tutorial.currentRank", "NEWBIE");
            return Unit.INSTANCE;
        });

        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.load(file);
        assertEquals("NEWBIE", reloaded.getString("rank.tutorial.currentRank"));
        assertTrue(new File(playerData, "corrupted").isDirectory());
    }
}
