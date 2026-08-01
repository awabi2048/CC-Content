package jp.awabi2048.cccontent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CCContentInitializationOrderTest {
    private static final Path PLUGIN_SOURCE = Path.of(
        "src/main/kotlin/jp/awabi2048/cccontent/CCContent.kt"
    );

    @Test
    void guiRuntimeContractIsCheckedBeforeInitializationAndRegistration() throws IOException {
        String source = Files.readString(PLUGIN_SOURCE, StandardCharsets.UTF_8);
        int onEnable = source.indexOf("override fun onEnable()");
        int contractCheck = source.indexOf("if (!verifyGuiRuntimeContract()) return", onEnable);
        int configurationSync = source.indexOf("synchronizeConfigurationResources()", onEnable);
        int startPlugin = source.indexOf("startPlugin()", onEnable);
        int guiApiUse = source.indexOf("CCSystem.getAPI().getMenuCommandService()", startPlugin);
        int listenerRegistration = source.indexOf("server.pluginManager.registerEvents", startPlugin);

        assertTrue(onEnable >= 0);
        assertTrue(contractCheck > onEnable);
        assertTrue(contractCheck < configurationSync);
        assertTrue(contractCheck < startPlugin);
        assertTrue(contractCheck < guiApiUse);
        assertTrue(contractCheck < listenerRegistration);
    }

    @Test
    void contractFailureDisablesPluginAndReturnsWithoutInitialization() throws IOException {
        String source = Files.readString(PLUGIN_SOURCE, StandardCharsets.UTF_8);

        assertTrue(source.contains("CCSystemAPI.GUI_RUNTIME_CONTRACT_VERSION"));
        assertTrue(source.contains("REQUIRED_GUI_RUNTIME_CONTRACT_VERSION = 5"));
        assertTrue(source.contains("CCSystem.getAPI().guiRuntimeContractVersion"));
        assertTrue(source.contains("catch (failure: LinkageError)"));
        assertTrue(source.contains("catch (failure: RuntimeException)"));
        assertTrue(source.contains("return disableForGuiRuntimeContractFailure(failure)"));
        assertTrue(source.contains("if (actual != expected)"));
        assertTrue(source.contains("logger.severe"));
        assertTrue(source.contains("server.pluginManager.disablePlugin(this)"));
    }
}
