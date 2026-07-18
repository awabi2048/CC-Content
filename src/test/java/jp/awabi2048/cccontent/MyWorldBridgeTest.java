package jp.awabi2048.cccontent;

import jp.awabi2048.cccontent.integration.myworld.DefaultMyWorldBridge;
import jp.awabi2048.cccontent.integration.myworld.MyWorldUnavailableReason;
import jp.awabi2048.cccontent.integration.myworld.WorldPointGrantResult;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MyWorldBridgeTest {
    @Test
    void distinguishesMissingPluginFromDisabledCapability() {
        var missing = bridge(false, true, true);
        var disabled = bridge(true, false, true);

        assertFalse(missing.isAvailable());
        assertEquals(
            MyWorldUnavailableReason.PLUGIN_MISSING,
            ((WorldPointGrantResult.Unavailable) missing.grantWorldPoints(UUID.randomUUID(), 1)).getReason()
        );
        assertEquals(
            MyWorldUnavailableReason.CAPABILITY_DISABLED,
            ((WorldPointGrantResult.Unavailable) disabled.grantWorldPoints(UUID.randomUUID(), 1)).getReason()
        );
    }

    @Test
    void reportsMissingServiceAndReturnsSuccessfulBalance() {
        var unavailable = bridge(true, true, false);
        var available = bridge(true, true, true);

        assertEquals(
            MyWorldUnavailableReason.SERVICE_UNAVAILABLE,
            ((WorldPointGrantResult.Unavailable) unavailable.grantWorldPoints(UUID.randomUUID(), 2)).getReason()
        );
        assertEquals(102, ((WorldPointGrantResult.Success) available.grantWorldPoints(UUID.randomUUID(), 2)).getBalance());
    }

    private static DefaultMyWorldBridge bridge(boolean plugin, boolean capability, boolean service) {
        return new DefaultMyWorldBridge(
            () -> plugin,
            () -> null,
            () -> null,
            () -> capability,
            () -> service,
            (playerId, amount) -> 100 + amount
        );
    }
}
