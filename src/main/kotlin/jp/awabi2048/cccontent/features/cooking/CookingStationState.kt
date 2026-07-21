package jp.awabi2048.cccontent.features.cooking

import kotlin.math.roundToLong

data class CookingStoredInput(
    val ingredientId: String,
    val amount: Int,
    val serializedItem: String,
    val containerRemainderMaterial: String? = null,
    val containerRemainderAmount: Int = 0
) {
    init {
        require(ingredientId.isNotBlank())
        require(amount > 0)
        require(serializedItem.isNotBlank())
        require(containerRemainderAmount >= 0)
        require((containerRemainderMaterial == null) == (containerRemainderAmount == 0))
    }
}

data class CookingOutputStack(
    val customItemId: String,
    val amount: Int,
    val failed: Boolean
) {
    init {
        require(customItemId.isNotBlank())
        require(amount > 0)
    }
}

data class CookingReservoir(
    val customItemId: String,
    val remaining: Int,
    val maximum: Int,
    val containerMaterial: String,
    val failed: Boolean
) {
    init {
        require(customItemId.isNotBlank())
        require(maximum in 1..3)
        require(remaining in 0..maximum)
        require(containerMaterial.isNotBlank())
    }
}

data class CookingStationSession(
    val recipeId: String,
    val starterId: String,
    val scale: Int,
    val startHeat: CookingHeat,
    val failureCommitted: Boolean,
    val originalInputs: List<CookingStoredInput>,
    val reservedWaterUnits: Int,
    val totalTicks: Long,
    val remainingTicks: Long,
    val state: CookingProcessState,
    val outputStacks: List<CookingOutputStack> = emptyList(),
    val reservoir: CookingReservoir? = null,
    val consumedWaterUnits: Int = 0
) {
    init {
        require(recipeId.isNotBlank())
        require(starterId.isNotBlank())
        require(scale in 1..5)
        require(originalInputs.isNotEmpty())
        require(reservedWaterUnits in 0..3)
        require(totalTicks > 0)
        require(remainingTicks in 0..totalTicks)
        require(consumedWaterUnits in 0..reservedWaterUnits)
        require(!(outputStacks.isNotEmpty() && reservoir != null))
    }
}

sealed interface CookingStationStep {
    data class Updated(val session: CookingStationSession) : CookingStationStep
    data class Completed(val session: CookingStationSession) : CookingStationStep
}

object CookingStationStateMachine {
    @JvmStatic
    fun start(
        recipe: CookingRecipeDefinition,
        starterId: String,
        scale: Int,
        actualHeat: CookingHeat,
        inputs: List<CookingStoredInput>,
        processingTimeReduction: Double
    ): CookingStationSession {
        require(recipe.station == CookingStation.PAN || recipe.station == CookingStation.CAULDRON)
        require(recipe.heat != null)
        require(processingTimeReduction in 0.0..1.0)
        val ticks = (recipe.durationSeconds * 20.0 * (1.0 - processingTimeReduction))
            .roundToLong().coerceAtLeast(1L)
        val failure = actualHeat != recipe.heat
        return CookingStationSession(
            recipe.id,
            starterId,
            scale,
            actualHeat,
            failure,
            inputs,
            recipe.waterUnits * scale,
            ticks,
            ticks,
            if (failure) CookingProcessState.PROCESSING_FAILURE else CookingProcessState.PROCESSING_NORMAL
        )
    }

    @JvmStatic
    fun tick(session: CookingStationSession, currentHeat: CookingHeat?): CookingStationStep {
        require(session.state in processingStates)
        val pausedState = when {
            currentHeat == null -> CookingProcessState.PAUSED_NO_HEAT
            currentHeat != session.startHeat -> CookingProcessState.PAUSED_WRONG_HEAT
            else -> null
        }
        if (pausedState != null) return CookingStationStep.Updated(session.copy(state = pausedState))
        val processingState = if (session.failureCommitted) {
            CookingProcessState.PROCESSING_FAILURE
        } else {
            CookingProcessState.PROCESSING_NORMAL
        }
        val remaining = (session.remainingTicks - 1).coerceAtLeast(0)
        val updated = session.copy(state = processingState, remainingTicks = remaining)
        return if (remaining == 0L) CookingStationStep.Completed(updated) else CookingStationStep.Updated(updated)
    }

    @JvmStatic
    fun finish(
        session: CookingStationSession,
        recipe: CookingRecipeDefinition,
        normalCustomItemId: String,
        failureCustomItemId: String,
        containerMaterial: String?
    ): CookingStationSession {
        require(session.remainingTicks == 0L)
        require(session.recipeId == recipe.id)
        val itemId = if (session.failureCommitted) failureCustomItemId else normalCustomItemId
        val failed = session.failureCommitted
        return when (recipe.resultKind) {
            CookingResultKind.ITEM -> session.copy(
                state = CookingProcessState.READY_ITEM,
                outputStacks = List(session.scale) { CookingOutputStack(itemId, 1, failed) }
            )
            CookingResultKind.BOWL, CookingResultKind.BOTTLE -> {
                requireNotNull(containerMaterial)
                val servings = if (recipe.station == CookingStation.CAULDRON) {
                    recipe.waterUnits * session.scale
                } else {
                    session.scale
                }
                session.copy(
                    state = CookingProcessState.READY_LIQUID,
                    reservoir = CookingReservoir(itemId, servings, servings, containerMaterial, failed)
                )
            }
        }
    }

    @JvmStatic
    fun cancel(session: CookingStationSession): CookingStationSession? {
        if (session.failureCommitted || session.state !in cancellableStates) return null
        val returned = session.originalInputs.map {
            CookingOutputStack(it.serializedItem, it.amount, failed = false)
        } + session.originalInputs.mapNotNull { input ->
            input.containerRemainderMaterial?.let {
                CookingOutputStack(it, input.containerRemainderAmount, failed = false)
            }
        }
        return session.copy(
            state = CookingProcessState.CANCELLED_RETURN,
            outputStacks = returned,
            reservoir = null
        )
    }

    @JvmStatic
    fun collectSolid(session: CookingStationSession, stackIndex: Int): CookingStationSession? {
        if (session.state != CookingProcessState.READY_ITEM && session.state != CookingProcessState.CANCELLED_RETURN) return null
        if (stackIndex !in session.outputStacks.indices) return null
        val remaining = session.outputStacks.toMutableList().also { it.removeAt(stackIndex) }
        return session.copy(
            state = if (remaining.isEmpty()) CookingProcessState.IDLE else session.state,
            outputStacks = remaining,
            consumedWaterUnits = if (session.state == CookingProcessState.READY_ITEM) {
                session.reservedWaterUnits
            } else {
                session.consumedWaterUnits
            }
        )
    }

    @JvmStatic
    fun collectLiquid(session: CookingStationSession): CookingStationSession? {
        if (session.state != CookingProcessState.READY_LIQUID) return null
        val reservoir = session.reservoir ?: return null
        if (reservoir.remaining <= 0) return null
        val next = reservoir.remaining - 1
        return session.copy(
            state = if (next == 0) CookingProcessState.IDLE else CookingProcessState.READY_LIQUID,
            reservoir = if (next == 0) null else reservoir.copy(remaining = next),
            consumedWaterUnits = (session.consumedWaterUnits + if (session.reservedWaterUnits > 0) 1 else 0)
                .coerceAtMost(session.reservedWaterUnits)
        )
    }

    private val processingStates = setOf(
        CookingProcessState.PROCESSING_NORMAL,
        CookingProcessState.PROCESSING_FAILURE,
        CookingProcessState.PAUSED_NO_HEAT,
        CookingProcessState.PAUSED_WRONG_HEAT
    )
    private val cancellableStates = setOf(
        CookingProcessState.PROCESSING_NORMAL,
        CookingProcessState.PAUSED_NO_HEAT,
        CookingProcessState.PAUSED_WRONG_HEAT
    )
}
