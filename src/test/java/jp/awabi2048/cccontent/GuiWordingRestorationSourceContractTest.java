package jp.awabi2048.cccontent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuiWordingRestorationSourceContractTest {
    private static final Path SOURCE_ROOT = Path.of("src/main/kotlin");

    @Test
    void restoredTranslationsAreResolvedFromLanguageKeys() throws IOException {
        var source = readKotlinSources();
        var restoredTexts = List.of(
            "デスチェストからアイテムを取り戻すことができます",
            "おあげちゃんが疲れてしまうので、連続で行うことはできません",
            "職業に就くと、おあげ神社への奉納が行えるようになります",
            "今日のタスクがすべて完了しました！",
            "クリックすると、すべてのプレイヤーが",
            "インベントリ内の対象アイテムを格納"
        );

        restoredTexts.forEach(text -> assertFalse(source.contains(text),
            () -> "翻訳済みGUI文言をKotlinへ直接記述しないこと: " + text));

        assertTrue(source.contains("wording.dead_chest.description"));
        assertTrue(source.contains("wording.delivery.locked"));
        assertTrue(source.contains("wording.part_time.completed"));
        assertTrue(source.contains("arena.ui.token_exchange.details_line"));
        assertTrue(source.contains("gui_style_action"));
        assertTrue(source.contains("option.unselected"));
    }

    private static String readKotlinSources() throws IOException {
        try (var paths = Files.walk(SOURCE_ROOT)) {
            var builder = new StringBuilder();
            for (var path : paths.filter(Files::isRegularFile).filter(it -> it.toString().endsWith(".kt")).toList()) {
                builder.append(Files.readString(path, StandardCharsets.UTF_8)).append('\n');
            }
            return builder.toString();
        }
    }
}
