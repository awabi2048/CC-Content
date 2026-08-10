package jp.awabi2048.cccontent.features.arena

fun interface ArenaLeaseClaimAcquirer {
    fun acquire(): Boolean
}

fun interface ArenaLeaseAction {
    fun run()
}

fun interface ArenaLeaseResourceAcquirer<T : Any> {
    fun acquire(): T?
}

fun interface ArenaLeaseResourceReleaser<T : Any> {
    fun release(resource: T)
}

/** セッション登録前に取得するClaimとプール資源を、所有権移譲まで一括管理します。 */
class ArenaStartResourceLease<T : Any>(
    private val claimRequired: Boolean,
    private val tryAcquireClaim: ArenaLeaseClaimAcquirer,
    private val releaseClaim: ArenaLeaseAction,
    private val acquireResource: ArenaLeaseResourceAcquirer<T>,
    private val releaseUnusedResource: ArenaLeaseResourceReleaser<T>
) : AutoCloseable {
    enum class Failure {
        CLAIM_BUSY,
        RESOURCE_UNAVAILABLE
    }

    private var claimAcquired = false
    private var resource: T? = null
    private var ownershipTransferred = false

    fun acquire(): Failure? {
        check(resource == null && !ownershipTransferred) { "Arena start resources were already acquired" }
        if (claimRequired) {
            if (!tryAcquireClaim.acquire()) return Failure.CLAIM_BUSY
            claimAcquired = true
        }

        return try {
            val acquired = acquireResource.acquire()
            if (acquired == null) {
                releaseClaimIfOwned()
                Failure.RESOURCE_UNAVAILABLE
            } else {
                resource = acquired
                null
            }
        } catch (error: Throwable) {
            releaseClaimIfOwned()
            throw error
        }
    }

    fun resource(): T = checkNotNull(resource) { "Arena start resource is not acquired" }

    /** 以降の解放責務がArenaSessionへ移った時点で呼び出します。 */
    fun transferOwnership() {
        check(resource != null) { "Arena start resource is not acquired" }
        ownershipTransferred = true
    }

    override fun close() {
        if (ownershipTransferred) return
        val acquired = resource
        resource = null
        try {
            if (acquired != null) {
                releaseUnusedResource.release(acquired)
            }
        } finally {
            releaseClaimIfOwned()
        }
    }

    private fun releaseClaimIfOwned() {
        if (!claimAcquired) return
        claimAcquired = false
        releaseClaim.run()
    }
}
