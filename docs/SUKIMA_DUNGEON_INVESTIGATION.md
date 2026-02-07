# SukimaDungeon 仕様調査レポート

**調査対象:** GitHub リポジトリ https://github.com/awabi2048/SukimaDungeon  
**調査日:** 2026年2月7日  
**比較対象:** CC-Content プロジェクト（D:\こた鯖\dev\cc-content）

---

## 目次
1. [ディレクトリ構造比較](#1-ディレクトリ構造比較)
2. [Kotlinコード構成](#2-kotlinコード構成)
3. [リソースファイル構成](#3-リソースファイル構成)
4. [主な差異点](#4-主な差異点)
5. [技術スタック](#5-技術スタック)
6. [推奨アクション](#6-推奨アクション)

---

## 1. ディレクトリ構造比較

### SukimaDungeon（GitHub版）

```
src/main/kotlin/awabi2048/sukimadungeon/
├── SukimaDungeon.kt           (プラグインメインクラス)
├── MazeCommand.kt             (コマンド実装)
├── DungeonSession.kt          (セッション管理)
├── DungeonManager.kt          (ワールド管理)
├── DungeonListener.kt         (イベントリスナー)
├── CustomItemManager.kt       (カスタムアイテム管理)
├── MarkerManager.kt           (マーカー管理)
├── PortalManager.kt           (ポータル管理)
├── BGMManager.kt              (BGM管理)
├── ScoreboardManager.kt       (スコアボード管理)
├── SproutManager.kt           (芽管理)
├── LangManager.kt             (言語管理)
├── MessageManager.kt          (メッセージ管理)
├── PlayerDataManager.kt       (プレイヤーデータ管理)
├── MenuCooldownManager.kt     (クールダウン管理)
│
├── events/
│   └── SproutBreakEvent.kt    (カスタムイベント)
│
├── generator/
│   ├── MazeGenerator.kt       (迷路生成エンジン)
│   ├── StructureBuilder.kt    (構造体ビルダー)
│   ├── StructureLoader.kt     (構造体ローダー)
│   ├── Theme.kt               (テーマ定義)
│   ├── ThemeBiomeProvider.kt  (バイオーム供給者)
│   └── VoidChunkGenerator.kt  (ボイドチャンクジェネレーター)
│
├── gui/
│   ├── DungeonEntranceGui.kt  (入口GUI)
│   ├── DungeonJoinGui.kt      (参加GUI)
│   ├── DungeonConfirmGui.kt   (確認GUI)
│   ├── DungeonExitGui.kt      (出口GUI)
│   └── TalismanConfirmGui.kt  (札確認GUI)
│
├── items/
│   └── ItemManager.kt         (アイテム管理)
│
├── listeners/
│   ├── CompassListener.kt     (コンパスリスナー)
│   ├── DungeonDownListener.kt (ダンジョン下降リスナー)
│   ├── EntranceListener.kt    (入口リスナー)
│   ├── GravityListener.kt     (重力リスナー)
│   ├── ItemPickupListener.kt  (アイテムピックアップリスナー)
│   ├── MobTargetListener.kt   (Mob対象リスナー)
│   ├── SproutListener.kt      (芽リスナー)
│   ├── TalismanListener.kt    (札リスナー)
│   └── TrapListener.kt        (トラップリスナー)
│
├── mobs/
│   ├── MobManager.kt          (Mob管理)
│   └── SpecialBehavior.kt     (特殊動作)
│
└── tasks/
    └── SpecialTileTask.kt     (特殊タイルタスク)
```

### CC-Content版（現在）

```
src/main/kotlin/jp/awabi2048/cccontent/
├── CCContent.kt               (プラグインメインクラス)
│
├── command/
│   ├── CCCommand.kt           (メインコマンド)
│   └── GiveCommand.kt         (Giveコマンド)
│
├── features/
│   ├── arena/
│   ├── rank/                  (ランクシステム)
│   │   ├── RankManager.kt
│   │   ├── command/RankCommand.kt
│   │   ├── impl/              (実装クラス群)
│   │   ├── listener/          (リスナー群)
│   │   ├── profession/        (職業システム)
│   │   ├── localization/      (言語管理)
│   │   └── tutorial/          (チュートリアル)
│   │
│   └── sukima_dungeon/        (スキマダンジョン機能)
│       ├── command/
│       │   └── SukimaDungeonCommand.kt
│       │
│       ├── マネージャークラス
│       │   ├── DungeonSession.kt
│       │   ├── DungeonSessionManager.kt
│       │   ├── DungeonTimerTask.kt
│       │   ├── BGMManager.kt
│       │   ├── MarkerManager.kt
│       │   ├── MobManager.kt
│       │   ├── PortalManager.kt
│       │   ├── ScoreboardManager.kt
│       │   ├── SpectatorManager.kt
│       │   ├── PlayerDataManager.kt
│       │   ├── PlayerStateManager.kt
│       │   ├── EffectManager.kt
│       │   ├── LootManager.kt
│       │   ├── AchievementManager.kt
│       │   └── CustomEnchantManager.kt
│       │
│       ├── generator/
│       │   ├── MazeGenerator.kt
│       │   ├── StructureBuilder.kt
│       │   ├── StructureLoader.kt
│       │   └── Theme.kt
│       │
│       ├── gui/
│       │   ├── DungeonEntranceGui.kt
│       │   └── GuiManager.kt
│       │
│       └── common/
│           ├── ConfigManager.kt
│           ├── DungeonManager.kt
│           ├── LangManager.kt
│           └── MessageManager.kt
│
├── items/
│   ├── CustomItem.kt          (基底インターフェース)
│   ├── CustomItemManager.kt
│   ├── arena/
│   ├── misc/
│   └── sukima_dungeon/
│       ├── SukimaItems.kt
│       ├── SukimaItemListener.kt
│       ├── SproutHarvestListener.kt
│       ├── DungeonTier.kt
│       ├── CompassListener.kt
│       ├── EntranceListener.kt
│       ├── TalismanListener.kt
│       └── common/
│           ├── ConfigManager.kt
│           ├── DungeonManager.kt
│           ├── LangManager.kt
│           └── MessageManager.kt
```

---

## 2. Kotlinコード構成

### 2.1 コマンド実装の比較

#### GitHub版（MazeCommand.kt）

**実装方式:**
```kotlin
class MazeCommand(
    private val plugin: SukimaDungeon,
    private val loader: StructureLoader
) : CommandExecutor, TabCompleter

override fun onCommand(): Boolean {
    // 4つのサブコマンド:
    - "reload"     : 設定再読み込み
    - "give"       : カスタムアイテム付与
    - "add_item"   : アイテムYAML登録
}

override fun onTabComplete(): List<String>? {
    // 3段階のTAB補完実装
}
```

**特徴:**
- シンプルで理解しやすい
- 管理者向けのコマンド
- TAB補完完全実装

#### CC-Content版（SukimaDungeonCommand.kt）

**実装方式:**
```kotlin
class SukimaDungeonCommand : CommandExecutor, TabCompleter

override fun onCommand(): Boolean {
    // 6つのサブコマンド:
    - "help"     : ヘルプ表示
    - "tier"     : ティア別ダンジョン開始
    - "status"   : セッションステータス表示
    - "info"     : ダンジョン情報表示
    - "stop"     : セッション終了（管理者向け）
}

override fun onTabComplete(): List<String> {
    // 動的なTAB補完
}
```

**特徴:**
- プレイヤー向けコマンドを充実
- セッション管理機能の公開
- より詳細なステータス表示
- 権限チェック機能あり

### 2.2 DungeonSessionの比較

#### GitHub版
```kotlin
data class DungeonSession(
    val playerUUID: UUID,
    val tier: DungeonTier,
    val themeName: String,
    val durationSeconds: Int,
    var collectedSprouts: Int = 0,
    var totalSprouts: Int = 0,
    var elapsedMillis: Long = 0,
    @Transient var lastUpdate: Long = System.currentTimeMillis(),
    val startLocation: Location? = null,
    val tileSize: Int = 16,
    val gridWidth: Int = 0,
    val gridLength: Int = 0,
    val dungeonWorldName: String? = null,
    val minibossMarkers: Map<Pair<Int, Int>, Location> = emptyMap(),
    val minibossTriggered: MutableSet<Pair<Int, Int>> = mutableSetOf(),
    val mobSpawnPoints: List<Location> = emptyList(),
    val spawnedMobs: MutableMap<Location, UUID> = mutableMapOf(),
    val restCells: Set<Pair<Int, Int>> = emptySet(),
    var isMultiplayer: Boolean = false,
    var isCollapsing: Boolean = false,
    var collapseRemainingMillis: Long = 0
)
```

**プロパティ数:** 約18個  
**特徴:**
- マルチプレイヤー対応（isMultiplayer フラグ）
- 座標ペア管理（Pair<Int, Int>）
- 詳細なMob情報管理
- 永続化対応（sessions.yml）

#### CC-Content版
```kotlin
data class DungeonSession(
    val playerUUID: UUID,
    val tier: DungeonTier,
    val themeName: String,
    val durationSeconds: Int,
    val totalSprouts: Int,
    val gridWidth: Int,
    val gridLength: Int,
    val worldName: String,
    val escapeLocation: Location? = null,
    val minibossMarkers: List<Location> = emptyList(),
    val mobSpawnPoints: List<Location> = emptyList(),
    val restCells: List<Location> = emptyList(),
    var collectedSprouts: Int = 0,
    var elapsedMillis: Long = 0,
    var lastUpdate: Long = System.currentTimeMillis(),
    var isCollapsing: Boolean = false,
    var collapseRemainingMillis: Long = 0
)
```

**プロパティ数:** 約16個  
**特徴:**
- シンプルなLocation管理
- マルチプレイヤー非対応
- メモリ効率重視
- ユーティリティメソッド充実

| 項目 | GitHub版 | CC-Content版 |
|------|---------|-----------|
| マーカー管理 | Map<Pair, Location> | List<Location> |
| マルチプレイ | ✓ サポート | ✗ 非サポート |
| 脱出地点 | startLocation | escapeLocation |
| 永続化 | ✓ 実装済み | ✗ 未実装 |
| ユーティリティ | 少ない | 多い（フォーマット等） |

### 2.3 マネージャー構成の比較

#### GitHub版の特徴
- **スタティック設計:** 全マネージャーが object singleton
- **動的プラグイン取得:** JavaPlugin.getPlugin(SukimaDungeon::class.java)
- **単純な責任分担:** 各マネージャーが明確に機能分離
- **依存性注入なし:** 直接参照

**マネージャー数:** 約14個

#### CC-Content版の特徴
- **階層化設計:** features/sukima_dungeon/ 配下に整理
- **明示的インスタンス化:** CCContent.kt で生成・管理
- **複雑な責任分担:** DungeonSessionManager が中心
- **依存性注入対応:** インターフェース中心設計
- **新機能が多い:** PlayerStateManager、SpectatorManager、AchievementManager等

**マネージャー数:** 約15個（より多機能）

### 2.4 リスナー実装の比較

#### GitHub版
```
listeners/ パッケージに統一管理：
- CompassListener
- DungeonDownListener
- EntranceListener
- GravityListener
- ItemPickupListener
- MobTargetListener
- SproutListener
- TalismanListener
- TrapListener

特徴: 9個のリスナーが listeners パッケージに集約
```

#### CC-Content版
```
items/sukima_dungeon/ に分散実装：
- CompassListener
- EntranceListener
- SproutHarvestListener
- SukimaItemListener
- TalismanListener

特徴: リスナーを機能別に分散、再利用性を重視
```

### 2.5 カスタムアイテム管理の比較

#### GitHub版
```kotlin
enum CustomItem {
    COMPASS_TIER_1/2/3
    BOOKMARK_BROKEN/WORN/FADED/NEW
    TALISMAN
    WORLD_SPROUT
    MARKER_TOOL
}

object CustomItemManager {
    // enum ベース管理
    // NamespacedKey 使用
}
```

**特徴:** シンプルで直感的な enum 管理

#### CC-Content版
```kotlin
interface CustomItem {
    val feature: String
    val id: String
    val displayName: String
    val lore: List<String>
    fun createItem(amount: Int): ItemStack
    fun matches(item: ItemStack): Boolean
}

class SproutItem : CustomItem { ... }
class CompassTier1Item : CustomItem { ... }

object CustomItemManager {
    fun register(item: CustomItem): Unit
    fun createItem(fullId: String, amount: Int): ItemStack?
    fun getAllItemIds(): Collection<String>
    // インターフェース ベース管理
}
```

**特徴:** インターフェース設計で拡張性重視

---

## 3. リソースファイル構成

### 3.1 設定ファイル（config.yml）

両版とも約60行で、以下の設定項目を含む：
```yaml
sizes:                    # ダンジョンサイズ設定（small/medium/large/huge）
mob_scaling:              # Mob強化比率
miniboss_chance:          # ミニボス出現確率
rest_chance:              # 休息エリア出現確率
spawn_rates:              # MOB/ITEMスポーン確率
mob_spawn:                # スポーン範囲設定
mob_detection:            # 敵検知範囲
sprout_per_tile:          # 1タイルあたりの芽数
restricted_commands:      # 制限コマンド
compass:                  # コンパス設定（ティア別）
bgm:                      # BGM設定
```

**差異:** ほぼ同一

### 3.2 言語ファイル

#### GitHub版（ja_jp.yml - 191行）
- メッセージキー数：約80個
- 完成度：高い
- コンテンツ：充実

#### CC-Content版（lang/sukima/ja_jp.yml - 66行）
- メッセージキー数：約30個
- 完成度：開発中
- コンテンツ：基本のみ

**特徴:** GitHub版は翻訳が完成度高く、CC-Content版は拡張予定と思われる

### 3.3 アイテムファイル（items.yml）

#### GitHub版
```yaml
items:
  rotten_flesh:
    material: ROTTEN_FLESH
    name: "&7腐った肉"
    weight: 10
    lore: [...]
  iron_nugget:
    material: IRON_NUGGET
    name: "&f鉄塊"
    weight: 5
  # 約20種類のドロップ定義
```

**定義数:** 約20種類

#### CC-Content版
同様の構造で実装

---

## 4. 主な差異点

### 4.1 アーキテクチャレベル

| 観点 | GitHub版 | CC-Content版 |
|-----|---------|-----------|
| **プロジェクト構成** | 単一プラグイン | 統合プラグイン（多機能） |
| **パッケージ構造** | 浅い（awabi2048.sukimadungeon） | 深い（jp.awabi2048.cccontent.features.*） |
| **モジュール化** | なし | あり（ランク、チュートリアル等） |
| **設計パターン** | シンプル Singleton | Singleton + DI |

### 4.2 セッション永続化

**GitHub版：**
- ✓ 完全実装
- sessions.yml に保存
- サーバー再起動後のセッション復帰可能

**CC-Content版：**
- ✗ 未実装
- メモリのみ管理
- サーバー再起動でセッション消滅

### 4.3 マルチプレイヤー対応

**GitHub版：**
- ✓ サポート（isMultiplayer フラグ）
- 複数プレイヤーの同時進入可能

**CC-Content版：**
- ✗ 非対応
- 単一プレイヤーのみ

### 4.4 GUIシステム

| GitHub版 | CC-Content版 |
|---------|-----------|
| DungeonEntranceGui ✓ | DungeonEntranceGui ✓ |
| DungeonJoinGui ✓ | GuiManager（統合） |
| DungeonConfirmGui ✓ | （統合予定？） |
| DungeonExitGui ✓ | （未実装） |
| TalismanConfirmGui ✓ | （未実装） |

**特徴:** GitHub版はGUI多く実装済み。CC-Content版はGuiManagerで一元管理する設計。

### 4.5 マーカー管理方式

**GitHub版（高度）:**
```kotlin
minibossMarkers: Map<Pair<Int, Int>, Location>
minibossTriggered: MutableSet<Pair<Int, Int>>
// グリッド座標でマーカー管理（複雑だが強力）
```

**CC-Content版（シンプル）:**
```kotlin
minibossMarkers: List<Location>
// Location直接管理（シンプルだが機能限定）
```

### 4.6 ジェネレーター実装

**GitHub版（完全）:**
- MazeGenerator（迷路生成）
- StructureBuilder（構造体構築）
- StructureLoader（テーマ/構造体読み込み）
- Theme（テーマ定義）
- ThemeBiomeProvider（バイオーム供給）
- VoidChunkGenerator（ボイドワールド生成）

**CC-Content版（基本）:**
- MazeGenerator（迷路生成）
- StructureBuilder（構造体構築）
- StructureLoader（テーマ/構造体読み込み）
- Theme（テーマ定義）
- ThemeBiomeProvider（確認不可）
- VoidChunkGenerator（確認不可）

---

## 5. 技術スタック

| 項目 | GitHub版 | CC-Content版 |
|------|---------|-----------|
| **Java版** | 21 | 21 |
| **Kotlin版** | 2.0.20 | 2.0.20+ |
| **Paper API** | 1.21.4 | 1.21.8 |
| **ビルドツール** | Maven | Maven |
| **アーキテクチャ** | シンプル Singleton | 複雑（機能別モジュール） |
| **設計パターン** | Singleton | Singleton + DI |

---

## 6. 推奨アクション

### 優先度1（重要）

#### 1. セッション永続化の実装
- GitHub版の sessions.yml 永続化を参考に実装
- DungeonSessionManager に save/load メソッド追加
- サーバー再起動時のセッション復帰対応

```kotlin
// 参考: GitHub版の設計
fun saveSessions() {
    val sessionsData: MutableMap<String, Any> = mutableMapOf()
    for ((uuid, session) in sessions) {
        sessionsData[uuid.toString()] = session.toMap()
    }
    // sessions.yml に保存
}

fun loadSessions() {
    // sessions.yml から読み込み
    // 既存セッションを復帰
}
```

#### 2. マーカー管理の強化
- GitHub版の Map<Pair<Int, Int>, Location> 設計を参考
- より詳細な位置情報管理
- マルチプレイヤー対応の準備

### 優先度2（重要）

#### 3. GUIシステムの統合
- DungeonExitGui の実装
- TalismanConfirmGui の実装
- GuiManager の拡張

#### 4. 言語ファイルの充実
- ja_jp.yml を最低100行以上に拡張
- en_us.yml の実装
- GitHub版の191行を参考に内容充実

#### 5. マルチプレイヤー対応検討
- GitHub版の isMultiplayer フラグを参考
- 複数プレイヤーの同時ダンジョン進入対応
- セッション管理の拡張

### 優先度3（拡張機能）

#### 6. マネージャー機能の拡張
- PlayerStateManager の充実
- SpectatorManager（観戦機能）の検証
- AchievementManager（実績）の実装検証

#### 7. ジェネレーター機能の検証
- ThemeBiomeProvider の動作確認
- VoidChunkGenerator の動作確認
- テーマ/構造体の拡張性確認

---

## 7. 統合戦略（推奨）

### アプローチ
CC-Content版は、GitHub版の完成度とシンプルさを取り入れながら、モジュール化の利点を保つ設計が理想的です。

### 実装順序
1. **セッション永続化** ← 最優先
2. **マルチプレイヤー対応検討**
3. **GUIシステム統合**
4. **言語ファイル充実**
5. **マーカー管理強化**

### 技術的推奨事項
- GitHub版の sessions.yml 永続化コードを直接参考にしない（ライセンス確認）
- 設計思想のみ参考にして、CC-Content の構造に合わせて実装
- 段階的に機能を拡張
- テストを充実させる

---

## 8. コード参考例

### セッション永続化（推奨実装）

```kotlin
object DungeonSessionManager {
    private val sessions: MutableMap<UUID, DungeonSession> = ConcurrentHashMap()
    private val dataFile = File(CCContent.instance.dataFolder, "sessions.yml")
    
    fun saveSessions() {
        val config = YamlConfiguration()
        for ((uuid, session) in sessions) {
            config.set("sessions.$uuid", serializeSession(session))
        }
        config.save(dataFile)
    }
    
    fun loadSessions() {
        if (!dataFile.exists()) return
        val config = YamlConfiguration.loadConfiguration(dataFile)
        for (uuid in config.getConfigurationSection("sessions")?.getKeys(false) ?: emptySet()) {
            val session = deserializeSession(config.getConfigurationSection("sessions.$uuid"))
            sessions[UUID.fromString(uuid)] = session
        }
    }
}
```

---

## 9. まとめ

### GitHub版（SukimaDungeon）の強み
✓ シンプルかつ完成度が高い  
✓ マルチプレイヤー対応  
✓ セッション永続化完全実装  
✓ 言語ファイル充実（191行）  
✓ リスナー一元管理  

### CC-Content版の強み
✓ マルチプラグイン化（ランク、チュートリアル等）  
✓ 拡張性の高い設計  
✓ 機能別モジュール化  
✓ 新機能実装（Achievement、Spectator等）  
✓ Paper 1.21.8への対応  

### 今後の方向性
両プロジェクトの利点を組み合わせたハイブリッド設計を目指す。GitHub版の完成度とシンプルさを参考にしつつ、CC-Content版のモジュール化と拡張性を維持する。

