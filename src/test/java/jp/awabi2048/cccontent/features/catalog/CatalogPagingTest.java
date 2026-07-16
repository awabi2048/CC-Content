package jp.awabi2048.cccontent.features.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CatalogPagingTest {
    @Test
    void calculatesThirtySixItemPagesAndClampsBoundaries() {
        assertEquals(1, CatalogCommandKt.catalogTotalPages(0, 36));
        assertEquals(1, CatalogCommandKt.catalogTotalPages(36, 36));
        assertEquals(2, CatalogCommandKt.catalogTotalPages(37, 36));
        assertEquals(2, CatalogCommandKt.catalogTotalPages(72, 36));
        assertEquals(3, CatalogCommandKt.catalogTotalPages(73, 36));

        assertEquals(0, CatalogCommandKt.catalogPageFor(-1, 2));
        assertEquals(0, CatalogCommandKt.catalogPageFor(0, 2));
        assertEquals(1, CatalogCommandKt.catalogPageFor(1, 2));
        assertEquals(1, CatalogCommandKt.catalogPageFor(2, 2));
    }
}
