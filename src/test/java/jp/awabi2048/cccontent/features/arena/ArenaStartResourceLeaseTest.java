package jp.awabi2048.cccontent.features.arena;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ArenaStartResourceLeaseTest {
    @Test
    void claimConflictDoesNotAcquireOrConsumePoolResource() {
        var poolAcquireCount = new AtomicInteger();
        var poolReleaseCount = new AtomicInteger();
        var lease = lease(false, poolAcquireCount, poolReleaseCount);

        assertEquals(ArenaStartResourceLease.Failure.CLAIM_BUSY, lease.acquire());
        lease.close();

        assertEquals(0, poolAcquireCount.get());
        assertEquals(0, poolReleaseCount.get());
    }

    @Test
    void poolUnavailableReleasesClaim() {
        var claimReleased = new AtomicBoolean();
        var lease = new ArenaStartResourceLease<>(
                true,
                () -> true,
                () -> claimReleased.set(true),
                () -> null,
                ignored -> {});

        assertEquals(ArenaStartResourceLease.Failure.RESOURCE_UNAVAILABLE, lease.acquire());

        assertEquals(true, claimReleased.get());
    }

    @Test
    void failureBeforeOwnershipTransferReleasesBothResources() {
        var poolAcquireCount = new AtomicInteger();
        var poolReleaseCount = new AtomicInteger();
        var lease = lease(true, poolAcquireCount, poolReleaseCount);

        assertNull(lease.acquire());
        lease.close();

        assertEquals(1, poolAcquireCount.get());
        assertEquals(1, poolReleaseCount.get());
    }

    @Test
    void transferredResourcesAreLeftForSessionCleanup() {
        var poolAcquireCount = new AtomicInteger();
        var poolReleaseCount = new AtomicInteger();
        var lease = lease(true, poolAcquireCount, poolReleaseCount);

        assertNull(lease.acquire());
        lease.transferOwnership();
        lease.close();

        assertEquals(1, poolAcquireCount.get());
        assertEquals(0, poolReleaseCount.get());
    }

    private ArenaStartResourceLease<String> lease(
            boolean claimAvailable,
            AtomicInteger poolAcquireCount,
            AtomicInteger poolReleaseCount
    ) {
        return new ArenaStartResourceLease<>(
                true,
                () -> claimAvailable,
                () -> {},
                () -> {
                    poolAcquireCount.incrementAndGet();
                    return "arena.1";
                },
                ignored -> poolReleaseCount.incrementAndGet());
    }
}
