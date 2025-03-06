package com.ubivismedia.aircraft

import org.bukkit.*
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

class AircraftPlugin : JavaPlugin(), Listener {
    private lateinit var connection: Connection

    override fun onEnable() {
        logger.info("Aircraft Design Plugin enabled!")
        getCommand("aircraft")?.setExecutor(AircraftCommand(this))
        server.pluginManager.registerEvents(this, this)
        setupDatabase()
    }

    override fun onDisable() {
        logger.info("Aircraft Design Plugin disabled!")
        connection.close()
    }

    private fun setupDatabase() {
        val url = "jdbc:sqlite:plugins/AircraftPlugin/aircrafts.db"
        connection = DriverManager.getConnection(url)
        connection.createStatement().executeUpdate("""
            CREATE TABLE IF NOT EXISTS aircrafts (
                player_uuid TEXT,
                name TEXT,
                seats_per_row INTEGER,
                row_count INTEGER,
                PRIMARY KEY (player_uuid, name)
            )
        """)
        connection.createStatement().executeUpdate("""
            CREATE TABLE IF NOT EXISTS aircraft_blocks (
                aircraft_name TEXT,
                player_uuid TEXT,
                x INTEGER,
                y INTEGER,
                z INTEGER,
                material TEXT,
                PRIMARY KEY (aircraft_name, player_uuid, x, y, z)
            )
        """)
    }

    fun getConnection(): Connection {
        return connection
    }
    

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (event.view.title != "Select Aircraft Material") return

        event.isCancelled = true
        val clickedItem = event.currentItem ?: return
        val material = clickedItem.type

        player.sendMessage("You selected ${material.name.replace("_", " ")} as your aircraft material.")
        saveMaterialSelection(player, material)
        player.closeInventory()

        buildAircraft(player, material)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val clickedBlock = event.clickedBlock ?: return

        if (clickedBlock.type == Material.STONE_SLAB) {
            player.sendMessage("You have taken a seat in the aircraft.")
            player.teleport(clickedBlock.location.add(0.5, 0.5, 0.5))
        }
    }

     fun saveMaterialSelection(player: Player, material: Material) {
        val sql = "UPDATE aircrafts SET material = ? WHERE player_uuid = ? ORDER BY rowid DESC LIMIT 1"
        val statement: PreparedStatement = connection.prepareStatement(sql)
        statement.setString(1, material.name)
        statement.setString(2, player.uniqueId.toString())
        statement.executeUpdate()
        statement.close()
    }

     fun buildAircraft(player: Player, material: Material) {
        val world = player.world
        val startX = player.location.blockX
        val startY = player.location.blockY + 5
        val startZ = player.location.blockZ

        // Create cockpit with console
        world.getBlockAt(startX, startY, startZ).type = Material.GLASS
        world.getBlockAt(startX, startY + 1, startZ).type = Material.GLASS

        // Create fuselage
        world.getBlockAt(startX, startY, startZ - 1).type = Material.STONE_BUTTON // Simulating control panel
        world.getBlockAt(startX, startY, startZ - 2).type = Material.LEVER // Simulating flight controls

        // Create fuselage with slight curvature
        for (x in -1..1) {
            for (z in -4..4) {
                val heightOffset = if (x == 0) 1 else 0
                world.getBlockAt(startX + x, startY + heightOffset, startZ + z).type = material
            }
        }

        // Create wings with a slight upward tilt
        for (z in -2..2) {
            for (x in -3..-2) {
                world.getBlockAt(startX + x, startY + 2, startZ + z).type = material
            }
            for (x in 2..3) {
                world.getBlockAt(startX + x, startY + 2, startZ + z).type = material
            }
        }

        // Create tail fin with additional stability
        world.getBlockAt(startX, startY + 2, startZ + 4).type = material
        world.getBlockAt(startX, startY + 3, startZ + 4).type = material
        world.getBlockAt(startX, startY + 4, startZ + 4).type = material

        // Create passenger seats with spacing
        for (z in -3..3 step 2) {
            world.getBlockAt(startX, startY + 1, startZ + z).type = Material.STONE_SLAB
            world.getBlockAt(startX - 1, startY + 1, startZ + z).type = Material.STONE_SLAB
            world.getBlockAt(startX + 1, startY + 1, startZ + z).type = Material.STONE_SLAB
        }

        // Create cockpit seats
        world.getBlockAt(startX, startY + 1, startZ - 4).type = Material.STONE_SLAB
        world.getBlockAt(startX - 1, startY + 1, startZ - 4).type = Material.STONE_SLAB

        player.sendMessage("Your aircraft has been built using ${material.name.replace("_", " ")}")
    }
}

class AircraftCommand(private val plugin: AircraftPlugin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only players can use this command!")
            return true
        }

        if (args.isNullOrEmpty()) {
            sender.sendMessage("Usage: /aircraft design <Name> <Sitze pro Reihe> <Reihenanzahl>")
            return true
        }

        when (args[0].lowercase()) {
            "design" -> {
                if (args.size < 4) {
                    sender.sendMessage("Usage: /aircraft design <Name> <Sitze pro Reihe> <Reihenanzahl>")
                    return true
                }
                val name = args[1]
                val seatsPerRow = args[2].toIntOrNull()
                val rowCount = args[3].toIntOrNull()

                if (seatsPerRow == null || rowCount == null) {
                    sender.sendMessage("Invalid number format for seats or rows!")
                    return true
                }

                 fun openMaterialSelectionMenu(player: Player, name: String, seatsPerRow: Int, rowCount: Int) {
                    val inventory: Inventory = Bukkit.createInventory(null, 27, "Select Aircraft Material")

                    val materials = listOf(
                        Material.IRON_BLOCK, Material.QUARTZ_BLOCK, Material.OAK_PLANKS,
                        Material.STONE, Material.SMOOTH_STONE, Material.BRICKS
                    )

                    for ((index, material) in materials.withIndex()) {
                        val item = ItemStack(material)
                        val meta = item.itemMeta
                        meta?.setDisplayName(material.name.replace("_", " "))
                        item.itemMeta = meta
                        inventory.setItem(index, item)
                    }

                    player.openInventory(inventory)
                    player.sendMessage("Select a material for your aircraft by clicking on an item in the menu.")
                }

                saveAircraft(sender, name, seatsPerRow, rowCount)
                sender.sendMessage("Creating and saving aircraft '$name' with $seatsPerRow seats per row and $rowCount rows...")
                createHangarWorld(sender, name)
                buildAircraftStructure(sender.world, name, sender.uniqueId.toString(), seatsPerRow, rowCount)
            }
            "load" -> {
                if (args.size < 2) {
                    sender.sendMessage("Usage: /aircraft load <Name>")
                    return true
                }
                val name = args[1]
                loadAircraft(sender, name)
            }
            else -> sender.sendMessage("Unknown subcommand! Use /aircraft design or /aircraft load")
        }
        return true
    }

    private fun buildAircraftStructure(world: World, aircraftName: String, playerUuid: String, seatsPerRow: Int, rowCount: Int) {
        val startX = 0
        val startY = 70
        val startZ = 0

        for (x in -1..seatsPerRow) {
            for (z in -1..rowCount) {
                val block = world.getBlockAt(startX + x, startY, startZ + z)
                block.type = Material.IRON_BLOCK
                saveBlockToDatabase(aircraftName, playerUuid, x, startY, z, block.type)
            }
        }

        for (x in 0 until seatsPerRow) {
            for (z in 0 until rowCount) {
                val block = world.getBlockAt(startX + x, startY + 1, startZ + z)
                block.type = Material.STONE_SLAB
                saveBlockToDatabase(aircraftName, playerUuid, x, startY + 1, z, block.type)
            }
        }
    }

    private fun saveBlockToDatabase(aircraftName: String, playerUuid: String, x: Int, y: Int, z: Int, material: Material) {
        val sql = "INSERT OR REPLACE INTO aircraft_blocks (aircraft_name, player_uuid, x, y, z, material) VALUES (?, ?, ?, ?, ?, ?)"
        val statement: PreparedStatement = plugin.getConnection().prepareStatement(sql)
        statement.setString(1, aircraftName)
        statement.setString(2, playerUuid)
        statement.setInt(3, x)
        statement.setInt(4, y)
        statement.setInt(5, z)
        statement.setString(6, material.name)
        statement.executeUpdate()
        statement.close()
    }

    private fun loadAircraft(player: Player, name: String) {
        val sql = "SELECT x, y, z, material FROM aircraft_blocks WHERE player_uuid = ? AND aircraft_name = ?"
        val statement: PreparedStatement = plugin.getConnection().prepareStatement(sql)
        statement.setString(1, player.uniqueId.toString())
        statement.setString(2, name)
        val resultSet: ResultSet = statement.executeQuery()

        while (resultSet.next()) {
            val x = resultSet.getInt("x")
            val y = resultSet.getInt("y")
            val z = resultSet.getInt("z")
            val material = Material.matchMaterial(resultSet.getString("material")) ?: Material.AIR
            player.world.getBlockAt(x, y, z).type = material
        }

        resultSet.close()
        statement.close()
        player.sendMessage("Aircraft '$name' loaded successfully.")
    }

    private fun openMaterialSelectionMenu(player: Player, name: String, seatsPerRow: Int, rowCount: Int) {
        val inventory: Inventory = Bukkit.createInventory(null, 27, "Select Aircraft Material")

        val materials = listOf(
            Material.IRON_BLOCK, Material.QUARTZ_BLOCK, Material.OAK_PLANKS,
            Material.STONE, Material.SMOOTH_STONE, Material.BRICKS
        )

        for ((index, material) in materials.withIndex()) {
            val item = ItemStack(material)
            val meta = item.itemMeta
            meta?.setDisplayName(material.name.replace("_", " "))
            item.itemMeta = meta
            inventory.setItem(index, item)
        }

        player.openInventory(inventory)
        player.sendMessage("Select a material for your aircraft by clicking on an item in the menu.")
    }

    private fun saveAircraft(player: Player, name: String, seatsPerRow: Int, rowCount: Int) {
        val sql = "INSERT OR REPLACE INTO aircrafts (player_uuid, name, seats_per_row, row_count) VALUES (?, ?, ?, ?)"
        val statement: PreparedStatement = plugin.getConnection().prepareStatement(sql)
        statement.setString(1, player.uniqueId.toString())
        statement.setString(2, name)
        statement.setInt(3, seatsPerRow)
        statement.setInt(4, rowCount)
        statement.executeUpdate()
        statement.close()
    }

    private fun createHangarWorld(player: Player, hangarName: String) {
        val worldName = "hangar_${player.uniqueId}"
        val world = Bukkit.createWorld(WorldCreator(worldName).environment(World.Environment.NORMAL))

        if (world != null) {
            world.setSpawnLocation(0, 65, 0)
            generateHangarPlatform(world)
            player.teleport(world.spawnLocation)
            player.sendMessage("Hangar '$hangarName' created! You have been teleported to your personal hangar.")
        } else {
            player.sendMessage("Failed to create hangar world.")
        }
    }

    private fun generateHangarPlatform(world: World) {
        val platformY = 64
        for (x in -20..20) {
            for (z in -20..20) {
                world.getBlockAt(x, platformY, z).type = Material.QUARTZ_BLOCK
            }
        }
    }
}
