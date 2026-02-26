package jp.awabi2048.cccontent.features.rank.skill.listeners

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectEngine
import jp.awabi2048.cccontent.features.rank.skill.handlers.WarriorBowPowerBoostHandler
import jp.awabi2048.cccontent.features.rank.skill.handlers.WarriorPiercingHandler
import jp.awabi2048.cccontent.features.rank.skill.handlers.WarriorSnipeHandler
import jp.awabi2048.cccontent.features.rank.skill.handlers.WarriorThreeWayHandler
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.Material
import org.bukkit.GameMode
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

class WarriorBowEffectListener : Listener {

    companion object {
        private val plugin by lazy { JavaPlugin.getPlugin(CCContent::class.java) }
        private val bowPowerDeltaKey by lazy { NamespacedKey(plugin, "warrior_bow_power_delta") }
        private val snipeDamageFactorKey by lazy { NamespacedKey(plugin, "warrior_snipe_damage_factor") }
        private val threeWayArrowKey by lazy { NamespacedKey(plugin, "warrior_three_way_arrow") }
        private val threeWaySideDamageMultiplierKey by lazy {
            NamespacedKey(plugin, "warrior_three_way_side_damage_multiplier")
        }
        private const val FULL_BOW_CHARGE_TICKS = 20.0
        private const val TICKS_PER_MILLISECOND = 1.0 / 50.0
        private const val ARROW_GRAVITY_PER_TICK = 0.05
        private const val MAX_GRAVITY_COMPENSATION_PER_TICK = 0.049
        private const val MAX_RANGE_COMPENSATION_TICKS = 100
        private const val CHARGE_BAR_SEGMENTS = 15
        private const val EXTRA_CHARGE_RELEASE_GRACE_MILLIS = 750L
        private const val THREE_WAY_ANGLE_DEGREES = 5.0
    }

    private data class SnipeFlightState(
        val arrow: AbstractArrow,
        val compensationPerTick: Double,
        var remainingTicks: Int
    )

    private data class ExtraChargeState(
        val startedAtMillis: Long,
        val maxChargeTicks: Double,
        var lastHandRaisedAtMillis: Long,
        var completionNotified: Boolean = false
    )

    private enum class ConsumptionStrategy {
        MATCHING,
        TYPE,
        ANY
    }

    private data class ThreeWaySettings(
        val arrowConsumption: Int,
        val sideDamageMultiplier: Double
    )

    private val bowDrawStartedAtMillis = mutableMapOf<UUID, Long>()
    private val extraChargeStates = mutableMapOf<UUID, ExtraChargeState>()
    private val snipeFlightStates = mutableMapOf<UUID, SnipeFlightState>()

    init {
        startSnipeFlightTask()
        startExtraChargeHudTask()
    }

    private fun startSnipeFlightTask() {
        object : BukkitRunnable() {
            override fun run() {
                if (snipeFlightStates.isEmpty()) {
                    return
                }

                val iterator = snipeFlightStates.entries.iterator()
                while (iterator.hasNext()) {
                    val state = iterator.next().value
                    val arrow = state.arrow
                    if (!arrow.isValid || arrow.isDead || arrow.isOnGround || state.remainingTicks <= 0) {
                        iterator.remove()
                        continue
                    }

                    val velocity = arrow.velocity
                    if (velocity.lengthSquared() <= 1.0E-8) {
                        iterator.remove()
                        continue
                    }

                    arrow.world.spawnParticle(
                        Particle.ENCHANTED_HIT,
                        arrow.location,
                        1,
                        0.0,
                        0.0,
                        0.0,
                        0.0
                    )

                    if (state.compensationPerTick > 0.0) {
                        velocity.y += state.compensationPerTick
                        arrow.velocity = velocity
                    }
                    state.remainingTicks -= 1
                }
            }
        }.runTaskTimer(plugin, 1L, 1L)
    }

    private fun startExtraChargeHudTask() {
        object : BukkitRunnable() {
            override fun run() {
                if (extraChargeStates.isEmpty()) {
                    return
                }

                val now = System.currentTimeMillis()
                val iterator = extraChargeStates.entries.iterator()
                while (iterator.hasNext()) {
                    val (playerUuid, state) = iterator.next()
                    val player = plugin.server.getPlayer(playerUuid)
                    if (player == null || !player.isOnline) {
                        iterator.remove()
                        continue
                    }

                    if (!player.isSneaking || !player.isHandRaised) {
                        if (!player.isSneaking) {
                            clearChargeSubtitle(player)
                            iterator.remove()
                            continue
                        }

                        clearChargeSubtitle(player)
                        if (now - state.lastHandRaisedAtMillis > EXTRA_CHARGE_RELEASE_GRACE_MILLIS) {
                            iterator.remove()
                        }
                        continue
                    }

                    state.lastHandRaisedAtMillis = now

                    val elapsedTicks = (now - state.startedAtMillis) * TICKS_PER_MILLISECOND
                    val ratio = (elapsedTicks / state.maxChargeTicks).coerceIn(0.0, 1.0)
                    val completed = ratio >= 1.0
                    if (completed && !state.completionNotified) {
                        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f)
                        state.completionNotified = true
                    }

                    val subtitle = buildChargeSubtitle(ratio, completed)
                    player.sendTitle("", subtitle, 0, 5, 0)
                }
            }
        }.runTaskTimer(plugin, 1L, 1L)
    }

    private fun buildChargeSubtitle(ratio: Double, completed: Boolean): String {
        if (completed) {
            return "§b" + "|".repeat(CHARGE_BAR_SEGMENTS)
        }

        val filled = (ratio.coerceIn(0.0, 1.0) * CHARGE_BAR_SEGMENTS).toInt().coerceIn(0, CHARGE_BAR_SEGMENTS)
        val empty = CHARGE_BAR_SEGMENTS - filled
        return "§a" + "|".repeat(filled) + "§7" + "|".repeat(empty)
    }

    private fun clearChargeSubtitle(player: Player) {
        player.sendTitle("", "", 0, 0, 0)
    }

    private fun calculateEffectiveRangeMultiplier(baseRangeMultiplier: Double, ratio: Double): Double {
        val clampedBase = baseRangeMultiplier.coerceAtLeast(1.0)
        val clampedRatio = ratio.coerceIn(0.0, 1.0)
        return 1.0 + (clampedBase - 1.0) * clampedRatio
    }

    private fun calculateGravityCompensationPerTick(rangeMultiplier: Double): Double {
        if (rangeMultiplier <= 1.0) {
            return 0.0
        }

        val compensation = ARROW_GRAVITY_PER_TICK * (1.0 - 1.0 / rangeMultiplier)
        return compensation.coerceIn(0.0, MAX_GRAVITY_COMPENSATION_PER_TICK)
    }

    private fun isThreeWayArrow(arrow: AbstractArrow): Boolean {
        return arrow.persistentDataContainer.get(threeWayArrowKey, PersistentDataType.BYTE)?.toInt() == 1
    }

    private fun markAsThreeWayArrow(arrow: AbstractArrow) {
        arrow.persistentDataContainer.set(threeWayArrowKey, PersistentDataType.BYTE, 1)
    }

    private fun setThreeWaySideDamageMultiplier(arrow: AbstractArrow, multiplier: Double) {
        arrow.persistentDataContainer.set(threeWaySideDamageMultiplierKey, PersistentDataType.DOUBLE, multiplier)
    }

    private fun getThreeWaySideDamageMultiplier(arrow: AbstractArrow): Double? {
        return arrow.persistentDataContainer.get(threeWaySideDamageMultiplierKey, PersistentDataType.DOUBLE)
    }

    private fun rotateAroundYAxis(velocity: org.bukkit.util.Vector, angleDegrees: Double): org.bukkit.util.Vector {
        val radians = Math.toRadians(angleDegrees)
        val cosValue = cos(radians)
        val sinValue = sin(radians)
        val x = velocity.x * cosValue - velocity.z * sinValue
        val z = velocity.x * sinValue + velocity.z * cosValue
        return org.bukkit.util.Vector(x, velocity.y, z)
    }

    private fun spawnThreeWayArrowCopy(sourceArrow: AbstractArrow, velocity: org.bukkit.util.Vector): AbstractArrow? {
        val copiedArrow = sourceArrow.copy(sourceArrow.location) as? AbstractArrow ?: return null
        copiedArrow.velocity = velocity
        return copiedArrow
    }

    private fun countMatchingConsumables(player: Player, template: ItemStack): Int {
        var total = 0
        for (slot in getAmmoCandidateSlots(player.inventory)) {
            val stack = player.inventory.getItem(slot)
            if (stack == null || stack.type.isAir) {
                continue
            }
            if (!stack.isSimilar(template)) {
                continue
            }
            total += stack.amount
        }
        return total
    }

    private fun countConsumablesByType(player: Player, type: Material): Int {
        var total = 0
        for (slot in getAmmoCandidateSlots(player.inventory)) {
            val stack = player.inventory.getItem(slot)
            if (stack == null || stack.type.isAir || stack.type != type) {
                continue
            }
            total += stack.amount
        }
        return total
    }

    private fun isBowAmmo(material: Material): Boolean {
        return material == Material.ARROW || material == Material.SPECTRAL_ARROW || material == Material.TIPPED_ARROW
    }

    private fun countAnyBowAmmo(player: Player): Int {
        var total = 0
        for (slot in getAmmoCandidateSlots(player.inventory)) {
            val stack = player.inventory.getItem(slot)
            if (stack == null || stack.type.isAir || !isBowAmmo(stack.type)) {
                continue
            }
            total += stack.amount
        }
        return total
    }

    private fun consumeMatchingConsumables(player: Player, template: ItemStack, amount: Int): Boolean {
        if (amount <= 0) {
            return true
        }

        val inventory = player.inventory
        var remaining = amount
        for (slot in getAmmoCandidateSlots(inventory)) {
            val stack = inventory.getItem(slot) ?: continue
            if (stack.type.isAir || !stack.isSimilar(template)) {
                continue
            }

            val toConsume = minOf(stack.amount, remaining)
            val newAmount = stack.amount - toConsume
            if (newAmount <= 0) {
                inventory.setItem(slot, null)
            } else {
                stack.amount = newAmount
                inventory.setItem(slot, stack)
            }

            remaining -= toConsume
            if (remaining <= 0) {
                return true
            }
        }

        return false
    }

    private fun consumeConsumablesByType(player: Player, type: Material, amount: Int): Boolean {
        if (amount <= 0) {
            return true
        }

        val inventory = player.inventory
        var remaining = amount
        for (slot in getAmmoCandidateSlots(inventory)) {
            val stack = inventory.getItem(slot) ?: continue
            if (stack.type.isAir || stack.type != type) {
                continue
            }

            val toConsume = minOf(stack.amount, remaining)
            val newAmount = stack.amount - toConsume
            if (newAmount <= 0) {
                inventory.setItem(slot, null)
            } else {
                stack.amount = newAmount
                inventory.setItem(slot, stack)
            }

            remaining -= toConsume
            if (remaining <= 0) {
                return true
            }
        }

        return false
    }

    private fun consumeAnyBowAmmo(player: Player, amount: Int): Boolean {
        if (amount <= 0) {
            return true
        }

        val inventory = player.inventory
        var remaining = amount
        for (slot in getAmmoCandidateSlots(inventory)) {
            val stack = inventory.getItem(slot) ?: continue
            if (stack.type.isAir || !isBowAmmo(stack.type)) {
                continue
            }

            val toConsume = minOf(stack.amount, remaining)
            val newAmount = stack.amount - toConsume
            if (newAmount <= 0) {
                inventory.setItem(slot, null)
            } else {
                stack.amount = newAmount
                inventory.setItem(slot, stack)
            }

            remaining -= toConsume
            if (remaining <= 0) {
                return true
            }
        }

        return false
    }

    private fun getAmmoCandidateSlots(inventory: PlayerInventory): IntArray {
        val slots = ArrayList<Int>(40)
        val storageSize = inventory.storageContents.size
        for (slot in 0 until storageSize) {
            slots.add(slot)
        }

        val offHandSlot = 40
        if (inventory.size > offHandSlot) {
            slots.add(offHandSlot)
        }

        return slots.toIntArray()
    }

    private fun resolvePiercingLevel(
        compiledEffects: jp.awabi2048.cccontent.features.rank.skill.CompiledEffects?,
        shooterUuid: UUID
    ): Int? {
        val piercingEntry = compiledEffects?.byType?.get(WarriorPiercingHandler.EFFECT_TYPE)?.firstOrNull()
        if (piercingEntry != null) {
            return piercingEntry.effect.getIntParam("max_pierce_count", 1).coerceIn(1, 127)
        }

        val playerProfession = runCatching { CCContent.rankManager.getPlayerProfession(shooterUuid) }.getOrNull()
            ?: return null
        if (playerProfession.profession != Profession.WARRIOR) {
            return null
        }

        val activationStates = playerProfession.skillActivationStates
        val piercingEnabled = activationStates["piercing_1"] ?: true
        if ("piercing_1" in playerProfession.acquiredSkills && piercingEnabled) {
            return 1
        }

        return null
    }

    private fun resolveThreeWaySettings(
        compiledEffects: jp.awabi2048.cccontent.features.rank.skill.CompiledEffects?,
        shooterUuid: UUID
    ): ThreeWaySettings? {
        val threeWayEntry = compiledEffects?.byType?.get(WarriorThreeWayHandler.EFFECT_TYPE)?.firstOrNull()
        if (threeWayEntry != null) {
            return ThreeWaySettings(
                arrowConsumption = threeWayEntry.effect.getIntParam("arrow_consumption", 1).coerceAtLeast(1),
                sideDamageMultiplier = threeWayEntry.effect.getDoubleParam("side_damage_multiplier", 1.0).coerceAtLeast(0.0)
            )
        }

        val playerProfession = runCatching { CCContent.rankManager.getPlayerProfession(shooterUuid) }.getOrNull()
            ?: return null
        if (playerProfession.profession != Profession.WARRIOR) {
            return null
        }

        val acquiredSkills = playerProfession.acquiredSkills
        val activationStates = playerProfession.skillActivationStates
        fun isEnabled(skillId: String): Boolean = activationStates[skillId] ?: true

        if ("3way_2" in acquiredSkills && isEnabled("3way_2")) {
            return ThreeWaySettings(arrowConsumption = 2, sideDamageMultiplier = 1.0)
        }
        if ("3way_1" in acquiredSkills && isEnabled("3way_1")) {
            return ThreeWaySettings(arrowConsumption = 3, sideDamageMultiplier = 1.0)
        }

        return null
    }

    @Suppress("UNUSED_PARAMETER")
    private fun applyArrowConsumptionModifiers(baseConsumption: Int, shooter: Player): Int {
        return baseConsumption.coerceAtLeast(1)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onBowInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }

        val player = event.player
        val hand = event.hand ?: EquipmentSlot.HAND
        val itemInUsedHand = when (hand) {
            EquipmentSlot.HAND -> player.inventory.itemInMainHand
            EquipmentSlot.OFF_HAND -> player.inventory.itemInOffHand
            else -> null
        } ?: return

        if (!itemInUsedHand.type.name.endsWith("BOW")) {
            bowDrawStartedAtMillis.remove(player.uniqueId)
            if (extraChargeStates.remove(player.uniqueId) != null) {
                clearChargeSubtitle(player)
            }
            return
        }

        bowDrawStartedAtMillis[player.uniqueId] = System.currentTimeMillis()
        if (extraChargeStates.remove(player.uniqueId) != null) {
            clearChargeSubtitle(player)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        val playerUuid = player.uniqueId

        if (!event.isSneaking) {
            if (extraChargeStates.remove(playerUuid) != null) {
                clearChargeSubtitle(player)
            }
            return
        }

        val compiledEffects = SkillEffectEngine.getCachedEffects(playerUuid) ?: return
        if (compiledEffects.profession != Profession.WARRIOR) {
            return
        }

        val snipeEntries = compiledEffects.byType[WarriorSnipeHandler.EFFECT_TYPE]
        if (snipeEntries.isNullOrEmpty()) {
            return
        }

        val maxChargeTime = snipeEntries
            .sumOf { it.effect.getDoubleParam("max_charge_time", 0.0).coerceAtLeast(0.0) }
        if (maxChargeTime <= 0.0) {
            return
        }

        if (!player.isHandRaised) {
            return
        }

        val drawStartedAt = bowDrawStartedAtMillis[playerUuid] ?: return
        val drawTicks = (System.currentTimeMillis() - drawStartedAt) * TICKS_PER_MILLISECOND
        if (drawTicks >= FULL_BOW_CHARGE_TICKS) {
            extraChargeStates[playerUuid] = ExtraChargeState(
                startedAtMillis = System.currentTimeMillis(),
                maxChargeTicks = maxChargeTime,
                lastHandRaisedAtMillis = System.currentTimeMillis()
            )
            player.playSound(player.location, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 1.4f)
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onShootBow(event: EntityShootBowEvent) {
        val shooter = event.entity as? Player ?: return
        val arrow = event.projectile as? AbstractArrow ?: return
        val bow = event.bow ?: return

        val compiledEffects = SkillEffectEngine.getCachedEffects(shooter.uniqueId)
        val profession = compiledEffects?.profession
            ?: runCatching { CCContent.rankManager.getPlayerProfession(shooter.uniqueId)?.profession }.getOrNull()
        if (profession != Profession.WARRIOR) {
            return
        }
        if (!bow.type.name.contains("BOW")) {
            return
        }

        val shooterUuid = shooter.uniqueId
        bowDrawStartedAtMillis.remove(shooterUuid)

        val fullChargeReached = event.force >= 1.0f
        val extraChargeState = extraChargeStates.remove(shooterUuid)
        if (extraChargeState != null) {
            clearChargeSubtitle(shooter)
        }
        if (fullChargeReached && shooter.isSneaking && extraChargeState != null && compiledEffects != null) {
            val snipeEntries = compiledEffects.byType[WarriorSnipeHandler.EFFECT_TYPE]
            if (!snipeEntries.isNullOrEmpty()) {
                val damageMultiplier = snipeEntries
                    .sumOf { it.effect.getDoubleParam("damage_multiplier", 0.0).coerceAtLeast(0.0) }
                val rangeMultiplier = snipeEntries
                    .maxOfOrNull { it.effect.getDoubleParam("range_multiplier", 1.0).coerceAtLeast(1.0) }
                    ?: 1.0

                if (extraChargeState.maxChargeTicks > 0.0) {
                    val additionalChargeTicks = (System.currentTimeMillis() - extraChargeState.startedAtMillis) * TICKS_PER_MILLISECOND
                    val ratio = (additionalChargeTicks / extraChargeState.maxChargeTicks).coerceIn(0.0, 1.0)

                    val damageFactor = (1.0 + ratio) * damageMultiplier
                    if (damageFactor > 0.0) {
                        arrow.persistentDataContainer.set(snipeDamageFactorKey, PersistentDataType.DOUBLE, damageFactor)
                    }

                    val effectiveRangeMultiplier = calculateEffectiveRangeMultiplier(rangeMultiplier, ratio)
                    val compensationPerTick = calculateGravityCompensationPerTick(effectiveRangeMultiplier)
                    snipeFlightStates[arrow.uniqueId] = SnipeFlightState(
                        arrow = arrow,
                        compensationPerTick = compensationPerTick,
                        remainingTicks = MAX_RANGE_COMPENSATION_TICKS
                    )

                    shooter.playSound(shooter.location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.5f)
                }
            }
        }

        val bonusPower = compiledEffects?.byType?.get(WarriorBowPowerBoostHandler.EFFECT_TYPE)
            ?.sumOf { it.effect.getDoubleParam("power", 0.0).coerceAtLeast(0.0) }
            ?: 0.0
        if (bonusPower > 0.0) {
            val actualPower = bow.getEnchantmentLevel(Enchantment.POWER).toDouble().coerceAtLeast(0.0)
            val effectivePower = actualPower + bonusPower
            val deltaDamage = enchantLevelBonusDamage(effectivePower) - enchantLevelBonusDamage(actualPower)
            if (deltaDamage > 0.0) {
                arrow.persistentDataContainer.set(bowPowerDeltaKey, PersistentDataType.DOUBLE, deltaDamage)
            }
        }

        val piercingLevel = resolvePiercingLevel(compiledEffects, shooter.uniqueId)
        if (piercingLevel != null && piercingLevel > 0) {
            arrow.pierceLevel = piercingLevel
        }

        val threeWaySettings = resolveThreeWaySettings(compiledEffects, shooter.uniqueId)
            ?: return
        val baseArrowConsumption = threeWaySettings.arrowConsumption
        val sideDamageMultiplier = threeWaySettings.sideDamageMultiplier
        if (baseArrowConsumption <= 0) {
            return
        }

        val totalArrowConsumption = if (event.shouldConsumeItem() && shooter.gameMode != GameMode.CREATIVE) {
            applyArrowConsumptionModifiers(baseArrowConsumption, shooter)
        } else {
            0
        }

        var additionalArrowConsumption = 0
        var consumableTemplate: ItemStack? = null
        var consumptionStrategy = ConsumptionStrategy.ANY
        if (totalArrowConsumption > 0) {
            consumableTemplate = event.consumable?.clone()
            val availableMatching = if (consumableTemplate != null && !consumableTemplate.type.isAir) {
                countMatchingConsumables(shooter, consumableTemplate)
            } else 0
            val availableByType = if (consumableTemplate != null && !consumableTemplate.type.isAir) {
                countConsumablesByType(shooter, consumableTemplate.type)
            } else 0
            val availableAny = countAnyBowAmmo(shooter)
            val availableCount = when {
                availableMatching >= totalArrowConsumption -> {
                    consumptionStrategy = ConsumptionStrategy.MATCHING
                    availableMatching
                }
                availableByType >= totalArrowConsumption -> {
                    consumptionStrategy = ConsumptionStrategy.TYPE
                    availableByType
                }
                availableAny >= totalArrowConsumption -> {
                    consumptionStrategy = ConsumptionStrategy.ANY
                    availableAny
                }
                else -> availableAny
            }
            if (availableCount < totalArrowConsumption) {
                return
            }

            additionalArrowConsumption = (totalArrowConsumption - 1).coerceAtLeast(0)
        }

        val mainVelocity = arrow.velocity
        if (mainVelocity.lengthSquared() <= 1.0E-8) {
            return
        }

        val leftVelocity = rotateAroundYAxis(mainVelocity, -THREE_WAY_ANGLE_DEGREES)
        val rightVelocity = rotateAroundYAxis(mainVelocity, THREE_WAY_ANGLE_DEGREES)

        val leftArrow = spawnThreeWayArrowCopy(arrow, leftVelocity) ?: return
        val rightArrow = spawnThreeWayArrowCopy(arrow, rightVelocity) ?: run {
            leftArrow.remove()
            return
        }

        if (additionalArrowConsumption > 0) {
            val consumed = when (consumptionStrategy) {
                ConsumptionStrategy.MATCHING -> {
                    if (consumableTemplate != null && !consumableTemplate.type.isAir) {
                        consumeMatchingConsumables(shooter, consumableTemplate, additionalArrowConsumption)
                    } else {
                        consumeAnyBowAmmo(shooter, additionalArrowConsumption)
                    }
                }
                ConsumptionStrategy.TYPE -> {
                    if (consumableTemplate != null && !consumableTemplate.type.isAir) {
                        consumeConsumablesByType(shooter, consumableTemplate.type, additionalArrowConsumption)
                    } else {
                        consumeAnyBowAmmo(shooter, additionalArrowConsumption)
                    }
                }
                ConsumptionStrategy.ANY -> consumeAnyBowAmmo(shooter, additionalArrowConsumption)
            }
            if (!consumed) {
                leftArrow.remove()
                rightArrow.remove()
                return
            }
        }

        if (piercingLevel != null && piercingLevel > 0) {
            leftArrow.pierceLevel = piercingLevel
            rightArrow.pierceLevel = piercingLevel
        }

        markAsThreeWayArrow(arrow)
        markAsThreeWayArrow(leftArrow)
        markAsThreeWayArrow(rightArrow)
        setThreeWaySideDamageMultiplier(leftArrow, sideDamageMultiplier)
        setThreeWaySideDamageMultiplier(rightArrow, sideDamageMultiplier)

        val mainSnipeState = snipeFlightStates[arrow.uniqueId]
        if (mainSnipeState != null) {
            snipeFlightStates[leftArrow.uniqueId] = mainSnipeState.copy(arrow = leftArrow)
            snipeFlightStates[rightArrow.uniqueId] = mainSnipeState.copy(arrow = rightArrow)
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onArrowDamage(event: EntityDamageByEntityEvent) {
        val arrow = event.damager as? AbstractArrow ?: return
        val target = event.entity as? LivingEntity
        if (target != null && isThreeWayArrow(arrow)) {
            target.noDamageTicks = 0
        }

        val deltaDamage = arrow.persistentDataContainer.get(bowPowerDeltaKey, PersistentDataType.DOUBLE)
        if (deltaDamage != null && deltaDamage > 0.0) {
            event.damage += deltaDamage
        }

        val damageFactor = arrow.persistentDataContainer.get(snipeDamageFactorKey, PersistentDataType.DOUBLE)
        if (damageFactor != null && damageFactor > 0.0) {
            event.damage *= damageFactor
        }

        val sideDamageMultiplier = getThreeWaySideDamageMultiplier(arrow)
        if (sideDamageMultiplier != null && sideDamageMultiplier >= 0.0) {
            event.damage *= sideDamageMultiplier
        }
    }

    @EventHandler
    fun onProjectileHit(event: ProjectileHitEvent) {
        val arrow = event.entity as? AbstractArrow ?: return
        snipeFlightStates.remove(arrow.uniqueId)

        if (!isThreeWayArrow(arrow)) {
            return
        }

        val hitEntity = event.hitEntity as? LivingEntity
        if (hitEntity != null) {
            hitEntity.noDamageTicks = 0
        }

        arrow.remove()
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val playerUuid = event.player.uniqueId
        bowDrawStartedAtMillis.remove(playerUuid)
        extraChargeStates.remove(playerUuid)
    }

    private fun enchantLevelBonusDamage(level: Double): Double {
        if (level <= 0.0) {
            return 0.0
        }
        return level * 0.5 + 0.5
    }
}
