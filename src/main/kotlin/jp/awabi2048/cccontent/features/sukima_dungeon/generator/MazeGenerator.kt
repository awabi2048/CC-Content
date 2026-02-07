package jp.awabi2048.cccontent.features.sukima_dungeon.generator

import java.util.*

object MazeGenerator {
    data class Cell(val x: Int, val z: Int, var visited: Boolean = false, val connections: MutableSet<Direction> = mutableSetOf())

    enum class Direction(val dx: Int, val dz: Int) {
        NORTH(0, -1), SOUTH(0, 1), EAST(1, 0), WEST(-1, 0);

        fun opposite(): Direction {
            return when (this) {
                NORTH -> SOUTH
                SOUTH -> NORTH
                EAST -> WEST
                WEST -> EAST
            }
        }
    }

    fun generate(width: Int, length: Int, startX: Int = 0, startZ: Int = 0): Array<Array<Cell>> {
        val grid = Array(width) { x -> Array(length) { z -> Cell(x, z) } }
        val stack = Stack<Cell>()
        val random = Random()

        val startCell = grid[startX.coerceIn(0, width - 1)][startZ.coerceIn(0, length - 1)]
        startCell.visited = true
        
        // Ensure starting cell (entrance) has all 4 connections if possible
        for (dir in Direction.values()) {
            val nx = startCell.x + dir.dx
            val nz = startCell.z + dir.dz
            if (nx in 0 until width && nz in 0 until length) {
                startCell.connections.add(dir)
                val neighbor = grid[nx][nz]
                neighbor.connections.add(dir.opposite())
                // Do NOT mark neighbor as visited here, so the maze can grow from/to them
            }
        }
        
        stack.push(startCell)

        while (stack.isNotEmpty()) {
            val current = stack.peek()
            val neighbors = getUnvisitedNeighbors(current, grid, width, length)

            if (neighbors.isNotEmpty()) {
                val (neighbor, direction) = neighbors[random.nextInt(neighbors.size)]
                current.connections.add(direction)
                neighbor.connections.add(direction.opposite())
                neighbor.visited = true
                stack.push(neighbor)
            } else {
                stack.pop()
            }
        }

        return grid
    }

    private fun getUnvisitedNeighbors(cell: Cell, grid: Array<Array<Cell>>, width: Int, length: Int): List<Pair<Cell, Direction>> {
        val neighbors = mutableListOf<Pair<Cell, Direction>>()

        for (dir in Direction.values()) {
            val nx = cell.x + dir.dx
            val nz = cell.z + dir.dz

            if (nx in 0 until width && nz in 0 until length && !grid[nx][nz].visited) {
                neighbors.add(grid[nx][nz] to dir)
            }
        }

        return neighbors
    }
}
