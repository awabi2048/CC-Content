# CC-Content

統一されたカスタムアイテム管理システムを備えたMinecraftプラグイン。
GulliverLight、KotaArena、SukimaDungeonを統合します。

## 概要

このプラグインは、複数のコンテンツフィーチャーを一つの統一されたシステムで管理します。

## 実装済みフィーチャー

### GulliverLight (misc)
- `misc.big_light` - ビッグライト
- `misc.small_light` - スモールライト

### KotaArena (arena)
- `arena.soul_bottle` - 魂の瓶
- `arena.booster` - ブースター
- `arena.mob_drop_sack` - モブドロップサック
- `arena.hunter_talisman` - ハンタータリスマン
- `arena.golem_talisman` - ゴーレムタリスマン

### SukimaDungeon (sukima_dungeon)
- `sukima_dungeon.sprout` - スプラウト
- `sukima_dungeon.compass` - ダンジョンコンパス
- `sukima_dungeon.talisman` - おあげちゃんの御札

## コマンド仕様

### `/cc give` - カスタムアイテム配布
```
/cc give <player> <feature.id> [amount]

例:
/cc give @s misc.big_light
/cc give @a arena.soul_bottle 10
/cc give PlayerName sukima_dungeon.sprout
```

**TabComplete対応**: プレイヤー名とアイテムID自動補完

## アーキテクチャ

### CustomItem インターフェース
全カスタムアイテムが実装する基本インターフェース。

```kotlin
interface CustomItem {
    val feature: String        // フィーチャー名
    val id: String            // アイテムID
    val displayName: String   // 表示名
    
    fun createItem(amount: Int): ItemStack
    fun matches(item: ItemStack): Boolean
    fun onRightClick(player: Player, event: PlayerInteractEvent)
    // ...
}
```

### CustomItemManager
全アイテムの一元管理。

```kotlin
object CustomItemManager {
    fun register(item: CustomItem)
    fun getItem(fullId: String): CustomItem?
    fun createItem(fullId: String, amount: Int): ItemStack?
    fun identify(item: ItemStack): CustomItem?
    // ...
}
```

## ディレクトリ構成

```
cc-content/
├── src/main/kotlin/jp/awabi2048/cccontent/
│   ├── items/
│   │   ├── CustomItem.kt
│   │   ├── CustomItemManager.kt
│   │   ├── misc/          # GulliverLight
│   │   ├── arena/         # KotaArena
│   │   └── sukima_dungeon/  # SukimaDungeon
│   ├── command/
│   │   ├── CCCommand.kt
│   │   └── GiveCommand.kt
│   └── CCContent.kt
├── pom.xml
└── README.md
```

## ビルド方法

```bash
mvn clean package
```

成果物: `target/cc-content-1.0.0.jar`

## インストール

1. `target/cc-content-1.0.0.jar` をサーバーの `plugins/` フォルダにコピー
2. サーバーを再起動

## 今後の実装予定

- **Phase 6**: アリーナゲームロジック
  - Generic クラス（セッション管理）
  - GameListener（ゲームイベント）
  - パーティシステム

- **Phase 7**: スキマダンジョンロジック
  - ダンジョン生成システム
  - セッション管理
  - リスナー実装

## 技術仕様

- **言語**: Kotlin
- **フレームワーク**: PaperMC 1.21.8
- **ビルド**: Maven
- **Java**: 21+

## 開発情報

### ブランチ戦略
- `master`: リリース用
- `integrate/cc-content`: 統合実装中

### コミット履歴
- Phase 1: 基盤構築
- Phase 2: GulliverLight統合
- Phase 3: KotaArenaアイテム統合
- Phase 4: SukimaDungeonアイテム統合
- Phase 5: 統合完了

## ライセンス

MIT License

## 作成者

awabi2048
