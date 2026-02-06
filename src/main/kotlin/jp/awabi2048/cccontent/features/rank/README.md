# ランクシステム (Rank System)

## 概要

ランクシステムは、アリーナ・スキマダンジョン・カスタムアイテムの3つの異なるコンテンツに対して、独立したランク管理機能を提供します。

## アーキテクチャ

### コアコンポーネント

#### 1. **RankTier** (`RankTier.kt`)
ランクの階級を定義します。Bronze I から Master まで16段階のティアが存在します。

- **属性**
  - `displayName`: 表示名（色コード付き）
  - `level`: ティアレベル（1～16）
  - `nextThreshold`: このティアに到達するために必要なスコア

- **主要メソッド**
  - `getTierByScore(score: Long)`: スコアに基づいてティアを取得

```
BRONZE_I → BRONZE_II → BRONZE_III 
  ↓         ↓           ↓
SILVER_I → SILVER_II → SILVER_III
  ↓         ↓           ↓
GOLD_I → GOLD_II → GOLD_III
  ↓         ↓           ↓
PLATINUM_I → PLATINUM_II → PLATINUM_III
  ↓           ↓             ↓
DIAMOND_I → DIAMOND_II → DIAMOND_III
  ↓           ↓             ↓
        MASTER (最高位)
```

#### 2. **RankType** (`RankType.kt`)
ランクを適用するコンテンツのタイプを定義します。

- `ARENA`: アリーナランク
- `SUKIMA_DUNGEON`: スキマダンジョンランク
- `CUSTOM_ITEM`: カスタムアイテムランク

#### 3. **PlayerRank** (`PlayerRank.kt`)
プレイヤーの個別のランク情報を保持します。

- **属性**
  - `playerUuid`: プレイヤーのUUID
  - `rankType`: ランクのタイプ
  - `score`: 現在のスコア
  - `tier`: 現在のティア
  - `lastUpdated`: 最後の更新日時

- **主要メソッド**
  - `addScore(amount: Long)`: スコアを追加（ティア変更を自動判定）
  - `resetScore()`: スコアをリセット
  - `getTierProgress()`: 現在のティアの進捗度（0.0～1.0）
  - `getScoreToNextTier()`: 次のティアまでの必要スコア

#### 4. **PlayerRankData** (`PlayerRankData.kt`)
プレイヤーの全ランク情報（3つのRankType）を統合管理します。

- **属性**
  - `playerUuid`: プレイヤーのUUID
  - `ranks`: RankTypeごとのPlayerRankマップ

- **主要メソッド**
  - `getRank(rankType: RankType)`: 指定されたRankTypeのランク情報を取得
  - `getTotalScore()`: 全ランクの総スコア
  - `getHighestTier()`: 最高のティア

#### 5. **RankManager** (`RankManager.kt`)
ランク情報の管理を行うインターフェース。実装クラスは`RankManagerImpl`

- **主要メソッド**
  - `getPlayerRankData(playerUuid)`: プレイヤーのランクデータを取得
  - `getPlayerRank(playerUuid, rankType)`: 特定のランク情報を取得
  - `addScore(playerUuid, rankType, score)`: スコアを追加
  - `getRanking(rankType, limit)`: ランキングを取得
  - `saveData()`: ストレージに保存
  - `loadData()`: ストレージから読み込み

#### 6. **RankStorage** (`RankStorage.kt`)
ランクデータの永続化を行うインターフェース。実装クラスは`JsonRankStorage`

- **主要メソッド**
  - `savePlayerRank(rankData)`: ランクデータを保存
  - `loadPlayerRank(playerUuid)`: ランクデータを読み込み
  - `getAllPlayerRanks(rankType)`: 全プレイヤーのランクデータを取得

### イベントシステム

#### 1. **PlayerScoreAddEvent**
プレイヤーのランクにスコアが追加されたときに発火します。

```kotlin
class PlayerScoreAddEvent(
    val player: Player,
    val rankType: RankType,
    val addedScore: Long,
    val oldScore: Long,
    val newScore: Long
)
```

#### 2. **PlayerRankChangeEvent**
プレイヤーのランクが変更されたときに発火します（昇格・降格）。

```kotlin
class PlayerRankChangeEvent(
    val player: Player,
    val rankType: RankType,
    val oldTier: RankTier,
    val newTier: RankTier,
    val currentScore: Long
)
```

#### 3. **PlayerRankUpEvent**
プレイヤーがランクアップしたときに発火します（昇格のみ）。

```kotlin
class PlayerRankUpEvent(
    val player: Player,
    val rankType: RankType,
    val fromTier: RankTier,
    val toTier: RankTier,
    val currentScore: Long
)
```

### ストレージ実装

#### YamlRankStorage
YAML形式でランクデータを保存します。既存の`playerdata/<uuid>.yml`ファイル内の`rank`セクションにランク情報を格納します。

**ファイル構造:**
```
plugins/CC-Content/playerdata/
  ├── {uuid1}.yml
  ├── {uuid2}.yml
  └── ...
```

**YAMLフォーマット:**
```yaml
# playerdata/550e8400-e29b-41d4-a716-446655440000.yml
rank:
  ARENA:
    score: 5000
    tier: SILVER_I
    lastUpdated: 1643000000000
  SUKIMA_DUNGEON:
    score: 3000
    tier: BRONZE_III
    lastUpdated: 1643000000000
  CUSTOM_ITEM:
    score: 0
    tier: BRONZE_I
    lastUpdated: 1643000000000
```

**特徴:**
- プレイヤーの他のデータと同じファイルで管理
- BukkitのYamlConfiguration APIで簡単に扱える
- 既存のplayerdataシステムとの統合が容易
- ファイルフォーマットが人間が読みやすい

## 使用方法

### 1. RankManagerの初期化

```kotlin
val storage = YamlRankStorage(plugin.dataFolder)
val rankManager = RankManagerImpl(storage)
```

### 2. スコアの追加

```kotlin
val tierChanged = rankManager.addScore(
    playerUuid,
    RankType.ARENA,
    100L  // 追加するスコア
)

if (tierChanged) {
    // ティアが変更された場合の処理
}
```

### 3. ランク情報の取得

```kotlin
// 全ランク情報を取得
val rankData = rankManager.getPlayerRankData(playerUuid)

// 特定のランク情報を取得
val arenaRank = rankManager.getPlayerRank(playerUuid, RankType.ARENA)
println("${arenaRank.tier.displayName} - ${arenaRank.score}点")
println("進捗: ${String.format("%.1f", arenaRank.getTierProgress() * 100)}%")
println("次のティアまで: ${arenaRank.getScoreToNextTier()}点")
```

### 4. ランキングの取得

```kotlin
val ranking = rankManager.getRanking(RankType.ARENA, limit = 10)
ranking.forEachIndexed { index, (uuid, score, tier) ->
    println("#${index + 1} - $uuid: ${tier.displayName} ($score点)")
}
```

### 5. イベントのリッスン

```kotlin
class MyRankListener : RankListener {
    override fun onScoreAdd(event: PlayerScoreAddEvent) {
        event.player.sendMessage("§a+${event.addedScore}点!")
    }
    
    override fun onRankUp(event: PlayerRankUpEvent) {
        event.player.sendMessage("§6${event.fromTier.displayName} → ${event.toTier.displayName}")
    }
    
    override fun onRankChange(event: PlayerRankChangeEvent) {
        // ランク変更時の処理
    }
}

Bukkit.getPluginManager().registerEvents(MyRankListener(), plugin)
```

## 今後の実装

このコンテンツ構造は、以下の機能の実装をサポートするように設計されています：

- [ ] ランク情報を表示するコマンド（`/rank info`, `/rank ranking`など）
- [ ] ランク報酬システム（ランクアップ時の報酬配布）
- [ ] ランクリセット機能（季節ごとのリセットなど）
- [ ] ランク保護機能（デマンク）
- [ ] ランク統計情報の表示
- [ ] MySQL/PostgreSQLなどのデータベースへの対応

## ティアシステム詳細

### スコア基準

| ティア | スコア | 必要スコア |
|--------|--------|-----------|
| BRONZE_I | 0～1,000 | 1,000 |
| BRONZE_II | 1,000～2,000 | 2,000 |
| BRONZE_III | 2,000～3,000 | 3,000 |
| SILVER_I | 3,000～5,000 | 5,000 |
| SILVER_II | 5,000～7,000 | 7,000 |
| SILVER_III | 7,000～9,000 | 9,000 |
| GOLD_I | 9,000～12,000 | 12,000 |
| GOLD_II | 12,000～15,000 | 15,000 |
| GOLD_III | 15,000～18,000 | 18,000 |
| PLATINUM_I | 18,000～22,000 | 22,000 |
| PLATINUM_II | 22,000～26,000 | 26,000 |
| PLATINUM_III | 26,000～30,000 | 30,000 |
| DIAMOND_I | 30,000～35,000 | 35,000 |
| DIAMOND_II | 35,000～40,000 | 40,000 |
| DIAMOND_III | 40,000～50,000 | 50,000 |
| MASTER | 50,000～ | ∞ |

（※ スコア基準は後ほどバランス調整が可能）
