# Rank Feature アーキテクチャ

このディレクトリは、`cc-content` のランク機能を実装する。
現在の実装は「ティアスコア制」ではなく、以下の段階型フローで構成される。

1. チュートリアルランク（`NEWBIE` → `ATTAINER`）
2. 職業選択（9職業）
3. 職業EXP・レベル進行
4. スキルツリー解放
5. 効果フレームワーク適用
6. プレステージ

## 1. 主要コンポーネント

### 1.1 Facade / 永続化

- `RankManager.kt`: 機能外部から利用する統合インターフェース
- `impl/RankManagerImpl.kt`: Tutorial / Profession 系の実処理を委譲するFacade実装
- `RankStorage.kt`: 永続化インターフェース
- `impl/YamlRankStorage.kt`: `playerdata/<uuid>.yml` に保存する実装

### 1.2 チュートリアルランク

- `tutorial/TutorialRank.kt`: ランク定義（NEWBIE, VISITOR, PIONEER, ADVENTURER, ATTAINER）
- `tutorial/PlayerTutorialRank.kt`: プレイヤーごとの進捗データ
- `tutorial/TutorialRankManager.kt`: チュートリアル管理インターフェース
- `impl/TutorialRankManagerImpl.kt`: 実装
- `tutorial/task/*`: 条件ローダ・判定器・進捗管理

### 1.3 職業・スキルツリー

- `profession/Profession.kt`: 9職業定義
- `profession/PlayerProfession.kt`: 職業進行データ
- `profession/ProfessionManager.kt`: 職業管理インターフェース
- `impl/ProfessionManagerImpl.kt`: 実装
- `profession/SkillTree.kt`: スキルツリーインターフェース
- `profession/skilltree/ConfigBasedSkillTree.kt`: YAMLロード実装
- `profession/SkillNode.kt`: ノード定義
- `profession/SkillTreeRegistry.kt`: ツリー登録

### 1.4 効果フレームワーク

- `skill/SkillEffect.kt`: 効果定義
- `skill/SkillEffectHandler.kt`: 効果ハンドラインターフェース
- `skill/SkillEffectRegistry.kt`: ハンドラ登録
- `skill/SkillEffectEngine.kt`: キャッシュ・合成・適用中核
- `skill/EffectContext.kt`: 適用コンテキスト
- `skill/CompiledEffects.kt`: プレイヤー別コンパイル結果
- `skill/handlers/*`: 個別効果実装
- `skill/listeners/*`: イベント適用層

### 1.5 プレステージ

- `prestige/PrestigeToken.kt`: プレステージトークン（PDC付きアイテム）
- `prestige/PrestigeTokenListener.kt`: 所持状態の監視と再評価

## 2. 職業一覧

`Profession.kt` の定義:

- `lumberjack`
- `brewer`
- `miner`
- `cook`
- `swordsman`
- `warrior`
- `farmer`
- `gardener`
- `carpenter`

## 3. 設定ファイル構成

### 3.1 スキルツリー

- `src/main/resources/config/rank/job/*.yml`

各ファイルは以下を持つ:

- `skills.settings.level.initialExp`
- `skills.settings.level.base`
- `skills.settings.level.maxLevel`
- `skills.settings.overviewIcon`
- `skills.settings.bossBarColor`
- ノード定義（`requiredLevel`, `children`, `effect`, `exclusiveBranch`, `activationToggleable`）

### 3.2 EXPテーブル

- `src/main/resources/config/rank/job_exp.yml`

ブロック破壊・討伐時EXPの対応表を定義する。

### 3.3 チュートリアル条件

- `src/main/resources/config/rank/tutorial_tasks.yml`

`play_time_min`, `kill_mobs`, `mine_blocks`, `vanilla_exp`, `items`, `kill_boss` などを定義する。

## 4. 効果フレームワークの実行モデル

### 4.1 評価モード

- `CACHED`: 事前コンパイル済み効果を参照
- `RUNTIME`: イベントごとに評価

### 4.2 合成ルール

`CombineRule`:

- `ADD`
- `MULTIPLY`
- `REPLACE`
- `MAX`
- `MAX_BY_DEPTH`

### 4.3 適用の流れ

1. スキル取得/転職/レベルアップ等で `SkillEffectEngine.rebuildCache()`
2. リスナーがイベント発火時に `CompiledEffects` を参照
3. 対応ハンドラが `EffectContext` で適用

## 5. 主要イベント

`event/RankEvent.kt` で以下を公開する:

- `TutorialRankUpEvent`
- `ProfessionSelectedEvent`
- `ProfessionChangedEvent`
- `ProfessionLevelUpEvent`
- `SkillAcquiredEvent`
- `PlayerExperienceGainEvent`
- `PrestigeSkillAcquiredEvent`
- `PrestigeExecutedEvent`

## 6. 進行フロー

```text
NEWBIE
  -> (tutorial_tasks.yml達成)
ATTAINER
  -> 職業選択
職業EXP蓄積
  -> レベルアップ
スキル解放
  -> 効果適用
最大レベル到達 + 条件達成
  -> プレステージ
```

## 7. データ保存

`playerdata/<uuid>.yml` の `rank` セクションに保存する。

- `rank.tutorial.*`
- `rank.profession.*`

代表フィールド:

- `profession`
- `acquiredSkills`
- `prestigeSkills`
- `currentExp`
- `activeSkillId`
- `skillSwitchMode`
- `skillActivationStates`
- `bossBarEnabled`
- `bossBarDisplayMode`

## 8. 実装時の注意

- 効果追加時は、`SkillEffectHandler` 実装 + `SkillEffectRegistry` 登録 + 対応YAML更新をセットで行う。
- 職業・スキル構成変更時は、旧データとの互換が必要かを先に判断する。
- 職業EXP判定は `job/` リスナー群で分かれているため、対象イベントをまたぐ副作用に注意する。
- `RankCommand.kt` は巨大ファイルのため、GUI・コマンド修正は局所化して変更範囲を限定する。
