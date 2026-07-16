package jp.awabi2048.cccontent.features.catalog;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogSourceContractTest {
    @Test
    void keepsFishingLayoutAndCatalogInteractionGuards() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/kotlin/jp/awabi2048/cccontent/features/catalog/CatalogCommand.kt"));

        assertTrue(source.contains("sevenColumnList(21)"));
        assertTrue(source.contains("layout.itemsPerPage"));
        assertTrue(source.contains("CatalogHolder(player.uniqueId, type, page, totalPages)"));
        assertTrue(source.contains("InventoryDragEvent"));
        assertTrue(source.contains("holder.page > 0"));
        assertTrue(source.contains("holder.page + 1 < holder.totalPages"));
        assertFalse(source.contains("mutableMapOf<UUID, Pair<CatalogType, Int>>()"));
        assertFalse(source.contains("GuiMenuItems.icon"));
        assertFalse(source.contains("GuiMenuItems.fillFramed"));
    }

}
