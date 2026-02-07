package jp.awabi2048.cccontent.features.sukima_dungeon.listeners

import jp.awabi2048.cccontent.features.sukima_dungeon.DungeonSessionManager
import jp.awabi2048.cccontent.features.sukima_dungeon.PlayerDataManager
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.plugin.java.JavaPlugin

class DungeonDownListener(private val plugin: JavaPlugin) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        val world = player.world
        if (!world.name.startsWith("dungeon_")) return

        val session = DungeonSessionManager.getSession(player) ?: return
        if (!session.isMultiplayer) return

        if (player.health - event.finalDamage <= 0) {
            event.isCancelled = true
            setDown(player)
        }
    }

    private fun setDown(player: Player) {
        val session = DungeonSessionManager.getSession(player) ?: return
        if (session.isDown) return

        session.isDown = true
        player.health = 20.0
        player.gameMode = GameMode.SPECTATOR

        // Drop player head
        val head = ItemStack(Material.PLAYER_HEAD)
        val meta = head.itemMeta as SkullMeta
        meta.owningPlayer = player
        head.itemMeta = meta
        val droppedHead = player.world.dropItemNaturally(player.location, head)
        droppedHead.isUnlimitedLifetime = true
        droppedHead.isInvulnerable = true

        // Spectate another survivor
        val survivors = player.world.players.filter { p ->
            p != player && DungeonSessionManager.getSession(p)?.isDown != true
        }

        if (survivors.isNotEmpty()) {
            val target = survivors.random()
            player.spectatorTarget = target
            player.sendMessage("§cあなたはダウンしました！ §f生存者をスペクテートしています。")
        } else {
            player.sendMessage("§cあなたはダウンしました！ §f全員がダウンしたため、ダンジョンから脱出します。")
            //全員ダウン時の処理（一旦シンプルに脱出）
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                jp.awabi2048.cccontent.features.sukima_dungeon.DungeonManager.escapeDungeon(player.world, "§c全員がダウンしたため、攻略に失敗しました。")
            }, 60L)
        }
    }

    @EventHandler
    fun onPlayerSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        if (player.gameMode != GameMode.SPECTATOR) return
        val session = DungeonSessionManager.getSession(player) ?: return
        if (!session.isDown) return
        if (!event.isSneaking) return // Shift pushed

        val survivors = player.world.players.filter { p ->
            p != player && DungeonSessionManager.getSession(p)?.isDown != true
        }.sortedBy { it.name }

        if (survivors.isEmpty()) return

        val currentTarget = player.spectatorTarget as? Player
        val nextIndex = if (currentTarget == null) {
            0
        } else {
            val currentIndex = survivors.indexOf(currentTarget)
            (currentIndex + 1) % survivors.size
        }

        player.spectatorTarget = survivors[nextIndex]
        player.sendMessage("§aスペクテート対象を切り替えました: §f${survivors[nextIndex].name}")
    }

    @EventHandler
    fun onTeleport(event: PlayerTeleportEvent) {
        val player = event.player
        val session = DungeonSessionManager.getSession(player) ?: return
        if (!session.isDown) return

        if (event.cause == PlayerTeleportEvent.TeleportCause.SPECTATE) {
            // Keep spectating
            return
        }
        
        // Prevent leaving spectator mode or manual teleport
        if (player.world.name.startsWith("dungeon_")) {
            val survivors = player.world.players.filter { p ->
                p != player && DungeonSessionManager.getSession(p)?.isDown != true
            }
            if (survivors.isNotEmpty()) {
                val currentTarget = player.spectatorTarget
                if (currentTarget == null || !survivors.contains(currentTarget)) {
                    player.spectatorTarget = survivors.random()
                }
            }
        }
    }

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        val session = DungeonSessionManager.getSession(player) ?: return
        if (!session.isDown) return
        if (!player.world.name.startsWith("dungeon_")) return

        val survivors = player.world.players.filter { p ->
            p != player && DungeonSessionManager.getSession(p)?.isDown != true
        }

        if (survivors.isNotEmpty()) {
            val target = player.spectatorTarget as? Player
            if (target == null || target.world != player.world || DungeonSessionManager.getSession(target)?.isDown == true) {
                player.spectatorTarget = survivors.random()
            }
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val session = DungeonSessionManager.getSession(player)
        if (session?.isDown == true) {
            if (player.world.name.startsWith("dungeon_")) {
                player.gameMode = GameMode.SPECTATOR
                // Re-find target in next tick
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    val survivors = player.world.players.filter { p ->
                        p != player && DungeonSessionManager.getSession(p)?.isDown != true
                    }
                    if (survivors.isNotEmpty()) {
                        player.spectatorTarget = survivors.random()
                    } else {
                        // All down or no one left
                        jp.awabi2048.cccontent.features.sukima_dungeon.DungeonManager.escapeDungeon(player.world, "§cダンジョンが既に終了しているか、生存者がいません。")
                        session.isDown = false
                    }
                }, 10L)
            } else {
                // Not in dungeon world (maybe server restarted and dungeon wiped)
                session.isDown = false
                player.gameMode = GameMode.SURVIVAL
            }
        }
    }
}
