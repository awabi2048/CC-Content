package jp.awabi2048.cccontent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ContentItemGrantIdSourceContractTest {
    @Test
    void generatedIdsFollowCcSystemRegistrySyntax() throws Exception {
        var source = Files.readString(Path.of("src/main/kotlin/jp/awabi2048/cccontent/command/ContentItemGrantProvider.kt"));
        assertTrue(source.contains("arena.mob_token:$it"));
        assertTrue(source.contains("arena.enchant_shard:$it"));
        assertFalse(source.contains("mob_token#$it"));
        assertFalse(source.contains("enchant_shard#$it"));
    }
}
