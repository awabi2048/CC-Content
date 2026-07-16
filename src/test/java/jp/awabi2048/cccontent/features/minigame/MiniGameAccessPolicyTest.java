package jp.awabi2048.cccontent.features.minigame;

import jp.awabi2048.cccontent.features.minigame.core.MiniGameAccessPolicy;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;

class MiniGameAccessPolicyTest {
    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OPERATOR = UUID.randomUUID();

    @Test
    void refusesAllEditingWhenMwmRepositoryIsUnavailable() {
        MiniGameAccessPolicy policy = new MiniGameAccessPolicy(() -> null);

        assertFalse(policy.canEdit(OPERATOR, WORLD, OWNER));
        assertFalse(policy.canView(OPERATOR, WORLD));
    }

    @Test
    void refusesTheUnsetPdcOwnerBeforeConsultingMwm() {
        MiniGameAccessPolicy policy = new MiniGameAccessPolicy(() -> {
            throw new AssertionError("zero PDC owner must be rejected locally");
        });

        assertFalse(policy.canEdit(OPERATOR, WORLD, new UUID(0L, 0L)));
    }
}
