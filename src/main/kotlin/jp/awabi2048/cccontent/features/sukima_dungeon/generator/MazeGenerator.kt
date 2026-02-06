package jp.awabi2048.cccontent.features.sukima_dungeon.generator

import kotlin.random.Random

/**
 * ダンジョン迷路生成クラス
 * Depth-First Search (DFS) アルゴリズムを使用して迷路を生成
 */
object MazeGenerator {
    
    /**
     * 迷路内のセル（タイル）を表すデータクラス
     */
    data class Cell(
        val x: Int,
        val z: Int,
        var visited: Boolean = false,
        val connections: MutableSet<Direction> = mutableSetOf()
    )
    
    /**
     * セル間の接続方向を表す列挙型
     */
    enum class Direction(val dx: Int, val dz: Int) {
        NORTH(0, -1),
        SOUTH(0, 1),
        EAST(1, 0),
        WEST(-1, 0);
        
        /**
         * 反対方向を取得
         */
        fun opposite(): Direction = when (this) {
            NORTH -> SOUTH
            SOUTH -> NORTH
            EAST -> WEST
            WEST -> EAST
        }
        
        companion object {
            fun random(): Direction = entries.random()
        }
    }
    
    /**
     * DFS アルゴリズムで迷路を生成
     * @param width グリッドの幅（X方向のセル数）
     * @param length グリッドの長さ（Z方向のセル数）
     * @param startX 開始セルのX座標
     * @param startZ 開始セルのZ座標
     * @return セル配列 [x][z]
     */
    fun generate(
        width: Int,
        length: Int,
        startX: Int = 0,
        startZ: Int = 0
    ): Array<Array<Cell>> {
        // グリッドを初期化
        val grid = Array(width) { x ->
            Array(length) { z ->
                Cell(x, z)
            }
        }
        
        // 開始セルを取得
        val startCell = grid[startX][startZ]
        startCell.visited = true
        
        // DFS スタック
        val stack = mutableListOf<Cell>()
        stack.add(startCell)
        
        // DFS ループ
        while (stack.isNotEmpty()) {
            val current = stack.last()
            
            // 未訪問の隣接セルを取得
            val unvisitedNeighbors = getUnvisitedNeighbors(grid, current, width, length)
            
            if (unvisitedNeighbors.isNotEmpty()) {
                // ランダムに隣接セルを選択
                val nextDirection = unvisitedNeighbors.random()
                val nextCell = getNeighbor(grid, current, nextDirection, width, length)
                    ?: continue
                
                // 現在のセルと次のセルを接続
                current.connections.add(nextDirection)
                nextCell.connections.add(nextDirection.opposite())
                
                // 次のセルを訪問済みにして スタックに追加
                nextCell.visited = true
                stack.add(nextCell)
            } else {
                // 未訪問の隣接セルがない場合、バックトラック
                stack.removeAt(stack.size - 1)
            }
        }
        
        return grid
    }
    
    /**
     * 指定セルの未訪問隣接セルの方向リストを取得
     */
    private fun getUnvisitedNeighbors(
        grid: Array<Array<Cell>>,
        cell: Cell,
        width: Int,
        length: Int
    ): List<Direction> {
        val neighbors = mutableListOf<Direction>()
        
        for (direction in Direction.entries) {
            val neighbor = getNeighbor(grid, cell, direction, width, length)
            if (neighbor != null && !neighbor.visited) {
                neighbors.add(direction)
            }
        }
        
        return neighbors
    }
    
    /**
     * 指定方向の隣接セルを取得
     */
    private fun getNeighbor(
        grid: Array<Array<Cell>>,
        cell: Cell,
        direction: Direction,
        width: Int,
        length: Int
    ): Cell? {
        val nextX = cell.x + direction.dx
        val nextZ = cell.z + direction.dz
        
        if (nextX < 0 || nextX >= width || nextZ < 0 || nextZ >= length) {
            return null
        }
        
        return grid[nextX][nextZ]
    }
    
    /**
     * デバッグ用：迷路をコンソールに出力
     */
    fun printMaze(grid: Array<Array<Cell>>) {
        for (z in grid[0].indices) {
            // 上部の壁
            for (x in grid.indices) {
                print("+-")
            }
            println("+")
            
            // セルと右側の壁
            for (x in grid.indices) {
                print("| ")
            }
            println("|")
        }
        
        // 下部の壁
        for (x in grid.indices) {
            print("+-")
        }
        println("+")
    }
}