package jp.awabi2048.cccontent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CCContentReloadLifecycleTest {
    private static final Path SOURCE = Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/CCContent.kt");

    @Test
    void reloadUsesTheCompleteStopValidateStartLifecycle() throws IOException {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        int reloadStart = source.indexOf("private fun reloadConfigFiles()");
        int lifecycleStart = source.indexOf("private fun restartPluginLifecycle(");
        int stop = source.indexOf("stopPlugin()", lifecycleStart);
        int synchronize = source.indexOf("synchronizeConfigurationResources()", lifecycleStart);
        int validation = source.indexOf("validateConfigurationFiles()", lifecycleStart);
        int start = source.indexOf("startPlugin()", lifecycleStart);

        assertTrue(reloadStart >= 0);
        assertTrue(lifecycleStart > reloadStart);
        assertTrue(stop < synchronize);
        assertTrue(synchronize < validation);
        assertTrue(validation < start);
        String reload = source.substring(reloadStart, lifecycleStart);
        assertFalse(reload.contains("reloadContentEnabledSettings()"));
        assertFalse(reload.contains("breweryFeature?.reload()"));
        assertFalse(reload.contains("cookingFeature?.reload()"));
        assertFalse(reload.contains("reloadSukimaDungeon()"));
    }

    @Test
    void completeStopCoversEveryNewFeatureAndCatalogRebuildsOnStart() throws IOException {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        int stopStart = source.indexOf("private fun stopPlugin()");
        int stopEnd = source.indexOf("override fun onEnable()", stopStart);
        String stop = source.substring(stopStart, stopEnd);
        String start = source.substring(source.indexOf("private fun startPlugin()"), stopStart);

        assertTrue(stop.contains("minigameRuntime?.shutdown()"));
        assertTrue(stop.contains("breweryFeature?.shutdown()"));
        assertTrue(stop.contains("cookingFeature?.shutdown()"));
        assertTrue(stop.contains("fishingFeature?.shutdown()"));
        assertTrue(stop.contains("resourceCollectionFeature?.shutdown()"));
        assertTrue(stop.contains("seasonalFeature?.shutdown()"));
        assertTrue(stop.contains("partyController?.close()"));
        assertTrue(start.contains("catalogStore = CatalogStore"));
        assertTrue(start.contains("registerCatalogCommands()"));
    }

    @Test
    void contentManagementUsesStrictSettingsAndCompleteLifecycle() throws IOException {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        String command = Files.readString(
                Path.of("src/main/kotlin/jp/awabi2048/cccontent/command/CCCommand.kt"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("ContentFeatureCatalog.features.associate"));
        assertTrue(source.contains("check(coreConfig.isBoolean(path))"));
        assertFalse(source.contains("getBoolean(\"content_enabled.arena\", true)"));
        assertTrue(source.contains("throw IllegalArgumentException(\"Unknown content feature:"));
        assertTrue(source.contains("CoreConfigManager.setContentEnabled(this, featureId, enabled)"));
        assertTrue(source.contains("restartPluginLifecycle(\"content_enabled.$featureId=$enabled\")"));
        assertTrue(command.contains("\"status\" -> handleContentStatus(sender)"));
        assertTrue(command.contains("\"enable\" -> handleContentStateChange(sender, args, true)"));
        assertTrue(command.contains("\"disable\" -> handleContentStateChange(sender, args, false)"));
        assertTrue(command.contains("if (!hasAdminPermission(sender))"));
    }

    @Test
    void unavailableCommandsAreInstalledBeforeFeatureInitialization() throws IOException {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        int unavailable = source.indexOf("installFeatureUnavailableCommands()", source.indexOf("private fun startPlugin()"));
        int party = source.indexOf("initializeFeatureIfEnabled(\"Party\"", source.indexOf("private fun startPlugin()"));
        int rank = source.indexOf("initializeFeatureIfEnabled(\"Rank System\"", source.indexOf("private fun startPlugin()"));

        assertTrue(unavailable >= 0);
        assertTrue(unavailable < party);
        assertTrue(unavailable < rank);
    }
}
