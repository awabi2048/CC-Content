package jp.awabi2048.cccontent.features.rank;

import jp.awabi2048.cccontent.features.rank.impl.YamlRankStorage;
import jp.awabi2048.cccontent.features.rank.profession.BossBarDisplayMode;
import jp.awabi2048.cccontent.features.rank.profession.PlayerProfession;
import jp.awabi2048.cccontent.features.rank.profession.Profession;
import jp.awabi2048.cccontent.features.rank.profession.profile.FishingInformationMode;
import jp.awabi2048.cccontent.features.rank.profession.profile.ProfessionCycleStatistics;
import jp.awabi2048.cccontent.features.rank.profession.profile.ProfessionFeatureToggles;
import jp.awabi2048.cccontent.features.rank.profession.profile.ProfessionPrestigeRecord;
import jp.awabi2048.cccontent.features.rank.skill.SkillSwitchMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlRankStorageV2Test {
    @TempDir
    Path tempDirectory;

    @Test
    void firstInitializationArchivesProfessionOnlyAndPreservesTutorial() throws Exception {
        UUID uuid = UUID.randomUUID();
        File playerData = tempDirectory.resolve("playerdata").toFile();
        assertTrue(playerData.mkdirs());
        File playerFile = new File(playerData, uuid + ".yml");
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("rank.tutorial.currentRank", "NEWBIE");
        legacy.set("rank.profession.profession", "miner");
        legacy.set("rank.profession.acquiredSkills", List.of("legacy_skill"));
        legacy.set("arena.unrelated", 42);
        legacy.save(playerFile);

        File oldProfession = tempDirectory.resolve("profession/miner").toFile();
        assertTrue(oldProfession.mkdirs());
        new File(oldProfession, uuid + ".yml").createNewFile();

        YamlRankStorage storage = new YamlRankStorage(tempDirectory.toFile());
        storage.init();

        YamlConfiguration current = YamlConfiguration.loadConfiguration(playerFile);
        assertEquals("NEWBIE", current.getString("rank.tutorial.currentRank"));
        assertEquals(42, current.getInt("arena.unrelated"));
        assertFalse(current.isConfigurationSection("rank.profession"));
        assertTrue(tempDirectory.resolve("rank-profession-schema-v2.applied").toFile().isFile());
        assertFalse(tempDirectory.resolve("profession").toFile().exists());
        assertTrue(tempDirectory.resolve("discarded").toFile().isDirectory());

        storage.init();
        assertEquals("NEWBIE", YamlConfiguration.loadConfiguration(playerFile).getString("rank.tutorial.currentRank"));
    }

    @Test
    void typedProfessionRoundTripDoesNotPersistLegacySkillState() {
        UUID uuid = UUID.randomUUID();
        YamlRankStorage storage = new YamlRankStorage(tempDirectory.toFile());
        storage.init();

        ProfessionFeatureToggles toggles = ProfessionFeatureToggles.Companion.defaultsFor(Profession.FISHER);
        toggles.setFishingInformationMode(FishingInformationMode.DETAIL);
        ProfessionCycleStatistics statistics = new ProfessionCycleStatistics(12, 3, 2, 1);
        ArrayList<ProfessionPrestigeRecord> records = new ArrayList<>();
        records.add(new ProfessionPrestigeRecord("fisher", "rod_handling", 1234L, 1, 12));
        PlayerProfession profession = new PlayerProfession(
            uuid,
            Profession.FISHER,
            new HashSet<>(List.of("must_not_be_saved")),
            123L,
            999L,
            true,
            BossBarDisplayMode.LONG,
            false,
            new HashSet<>(List.of("must_not_be_saved")),
            "must_not_be_saved",
            SkillSwitchMode.MENU_ONLY,
            new HashMap<>(),
            "rod_handling",
            toggles,
            statistics,
            records
        );

        storage.saveProfession(profession);
        PlayerProfession loaded = storage.loadProfession(uuid);
        assertNotNull(loaded);
        assertEquals(Profession.FISHER, loaded.getProfession());
        assertEquals("rod_handling", loaded.getSpecializationId());
        assertEquals(FishingInformationMode.DETAIL, loaded.getFeatureToggles().getFishingInformationMode());
        assertEquals(12, loaded.getCycleStatistics().getValidActions());
        assertEquals(1, loaded.getPrestigeRecords().size());
        assertTrue(loaded.getAcquiredSkills().isEmpty());
        assertTrue(loaded.getPrestigeSkills().isEmpty());
        assertNull(loaded.getActiveSkillId());

        File file = tempDirectory.resolve("playerdata/" + uuid + ".yml").toFile();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        assertEquals(2, yaml.getInt("rank.profession.schemaVersion"));
        assertFalse(yaml.contains("rank.profession.acquiredSkills"));
        assertFalse(yaml.contains("rank.profession.prestigeSkills"));
        assertFalse(yaml.contains("rank.profession.activeSkillId"));
        assertEquals("fisher", yaml.getString("rank.professionPrestige.0.professionId"));

        storage.deleteProfession(uuid);
        YamlConfiguration afterDelete = YamlConfiguration.loadConfiguration(file);
        assertFalse(afterDelete.isConfigurationSection("rank.profession"));
        assertEquals("fisher", afterDelete.getString("rank.professionPrestige.0.professionId"));
    }
}
