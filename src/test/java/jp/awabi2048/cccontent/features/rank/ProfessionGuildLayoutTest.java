package jp.awabi2048.cccontent.features.rank;

import jp.awabi2048.cccontent.features.rank.gui.ProfessionGuildLayout;
import jp.awabi2048.cccontent.features.rank.profession.Profession;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfessionGuildLayoutTest {

    @Test
    void placesEveryProfessionExactlyOnceInTheTwoCenterRows() {
        Map<Integer, Profession> slots = ProfessionGuildLayout.PROFESSION_SLOTS;

        assertEquals(
            Set.of(20, 21, 22, 23, 24, 29, 30, 31, 32, 33),
            slots.keySet()
        );
        assertEquals(Set.of(Profession.values()), new HashSet<>(slots.values()));
        assertEquals(Profession.FISHER, slots.get(23));
        assertEquals(Profession.COOK, slots.get(29));
    }
}
