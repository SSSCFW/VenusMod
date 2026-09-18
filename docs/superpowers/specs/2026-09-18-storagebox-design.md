# Storage Box Design

**Date:** 2026-09-18

## Goal

VenusMod に Minecraft 1.21.1 / NeoForge ネイティブの `Storage Box` を追加する。CurseForge の StorageBox Mod と公開されている Fabric 版の**挙動だけ**を参考にし、公開ソースや画像アセットはコピーしない。VenusMod 側のコード、GUI、テクスチャ、データ形式は独自実装とする。

## Scope

追加するアイテムは `venusmod:storage_box`。1個の Storage Box は常に1種類の ItemStack テンプレートだけを保持し、最大 `2,147,483,647` 個までカウントする。同じアイテムIDでも Components が異なるもの（名前、エンチャント、耐久、Potion内容、CustomData 等）は別物として扱い、登録済みテンプレートと完全一致するものだけを収納する。

Storage Box 自身、Shulker Box、Bundle、他の Storage Box のような再帰・巨大データを生みやすい収納アイテムは `venusmod:storage_box_blacklist` タグで禁止する。デフォルトタグには Storage Box 自身を必ず含める。

## Data Model

Storage Box の状態は専用 Data Component に保存し、サーバー/クライアント同期と ItemStack コピー・ドロップ・死亡時保持を自動で維持する。

- `venusmod:storage_box_template`: count=1 の ItemStack テンプレート。空箱では未設定。
- `venusmod:storage_box_count`: int、0〜2,147,483,647。
- `venusmod:storage_box_auto_collect`: boolean。初期値 true。

count が0になった場合は template を削除し、空箱状態へ戻す。収納対象判定では `ItemStack.isSameItemSameComponents` 相当の完全一致を使う。

## Core Operations

純Java寄りの `StorageBoxRules` と Minecraft依存の `StorageBoxData` を分ける。

`StorageBoxData` は以下を提供する。

- `insert(box, incoming, requested)`: 一致するアイテムを容量上限まで収納し、受入数を返す。
- `extract(box, requested)`: 最大 requested 個を同一Componentsの ItemStack として取り出す。
- `canAccept(box, stack)`: 空箱なら blacklist でなければtrue、登録済みならテンプレート完全一致時のみtrue。
- `toggleAutoCollect(box)`
- `storedCount(box)`, `template(box)`

演算はオーバーフローしないよう long で中間計算し、保存値だけ int に収める。

## Controls

### Storage Box をメインハンドに持つ場合

- 右クリック（空中）: Storage Box GUI を開く。
- `'`（アポストロフィ/コロン系キー）: プレイヤーインベントリから収納可能なアイテムを可能な限り箱へ入れる。
- Shift + `'`: 箱から1スタック分をプレイヤーインベントリへ取り出す。入らない分は所有者向けドロップ。
- Ctrl + `'`: 自動回収 ON/OFF。
- Shift + Ctrl + `'`: 箱から1スタック分を目の前へドロップする。

キーはクライアントで検出して C2S payload を送る。サーバー側で「メインハンドが Storage Box か」「プレイヤーが生存しているか」を再検査してから処理する。

### 右クリック対象がインベントリブロックの場合

チェスト、樽、ホッパー等の ItemHandler があるブロックを Storage Box で右クリックすると、箱の中身を対象インベントリへ可能な限り一括排出する。対象へ入らなかった残量は箱へ残す。ブロックの通常UIはこの操作時には開かない。

### 中身の直接使用

Storage Box に入っているアイテムがブロック/使用可能アイテムなら、箱を持ったまま通常の右クリック・ブロック右クリック・エンティティ右クリックをした際に、中身1スタックを仮想的に手に持たせて元アイテムの `use/useOn/useOnEntity` を呼び、消費数と返却物を箱へ反映する。

バケツ→空バケツ、ポーション→瓶などテンプレートと異なる返却物は箱には混ぜず、プレイヤーインベントリへ入れ、満杯なら所有者向けドロップにする。耐久やComponentsが変化してテンプレート不一致になったアイテムも同様に外へ返す。

## GUI

専用 `StorageBoxMenu` / `StorageBoxScreen` を追加する。

画面には以下を表示する。

- 中央に保存中アイテムの大きなアイコン
- 保存中アイテム名
- `現在個数 / 2,147,483,647`
- Large Chest換算値（54スロット×対象アイテムの最大スタック数）
- IN スロット: プレイヤーが置いた同一アイテムを即時収納する。
- OUT スロット: クリック/Shiftクリックで最大1スタック取り出す。
- 自動回収 ON/OFF ボタン
- プレイヤーインベントリ

GUIを開いている間にメインハンドの Storage Box が別ItemStackへ変わった場合は安全のため画面を閉じる。

## Auto Collect

NeoForge の item pickup イベントで、プレイヤーインベントリ内の Storage Box を走査する。

優先順位はホットバー0→8、メインインベントリの順。自動回収ONで、かつ対象アイテムを既に登録している箱を優先する。空箱は地面の拾得物から勝手にテンプレートを決めない。つまり自動回収は「登録済みの箱へ一致品を吸う」だけとする。

複数箱が同じテンプレートを持つ場合は前方スロットから満杯にする。全量収納できた場合は通常インベントリへ入れず、残量があれば残量だけバニラ拾得へ渡す。

## Inventory-wide Insert Key

`'` キーの一括収納では、箱自身のスロットを除き、プレイヤーインベントリ内のテンプレート一致品を全走査する。

空箱の場合は、メインハンド以外の最初の収納可能ItemStackをテンプレートとして採用する。登録済みの場合は一致品だけを収納する。Storage Box自身やblacklist対象は無視する。

## Visuals

既存Modのテクスチャはコピーせず、VenusMod独自の箱アイコンを追加する。

空箱は箱アイコン。中身がある場合、アイテム本体の通常モデルを表示した上に、右上へ小さな Storage Box バッジをオーバーレイする。非空時は glint を付ける。

1.21.1 NeoForgeで安全に実装するため、アイコン差し替えはカスタム ItemRenderer/GUI overlay 方式を使い、バニラ/他Modの item model JSON を複製しない。

ツールチップには以下を表示する。

- `Storage Box`
- 保存アイテム名
- 個数
- Large Chest換算
- 自動回収状態
- Shift押下時にキー操作ヘルプ

## Recipe and Creative Tab

レシピは作業台でチェスト8個を外周に配置し、中央を空ける形。

```
CCC
C C
CCC
```

結果は Storage Box 1個。Tools & Utilities 相当のCreative Tabへ追加する。

## Networking

`StorageBoxActionPayload` 1種類を使い、enum相当の action ID を送る。

- INSERT_ALL
- EXTRACT_STACK
- DROP_STACK
- TOGGLE_AUTO_COLLECT

サーバーが唯一の状態更新元。クライアントは表示と入力だけを担当する。

## Compatibility and Safety

- Storage Box 自身の入れ子は禁止。
- `storage_box_blacklist` タグで追加禁止アイテムを拡張可能。
- count は必ず0〜Integer.MAX_VALUEへクランプ。
- ItemStackテンプレートは常にcount=1で保存。
- GUI、キー操作、pickup、ブロック排出の全経路で同じ `StorageBoxData` を使い、挙動差を作らない。
- 専用コードは `dev.ssscfw.venusmod.storagebox` 配下へまとめ、王の財宝/SlashBladeのコードとは分離する。
- CreateやSlashBladeへの必須依存は追加しない。
- Minecraft 1.21.1 / NeoForge の既存ビルド構成を維持する。

## Testing

Minecraft非依存の `StorageBoxRulesChecks` で以下を検査する。

- 容量上限とオーバーフロー防止
- 空箱・登録済み箱の収納数
- 最大1スタック取り出し
- LC換算
- 不正countのクランプ

GameTest/ソース契約検査では以下を確認する。

- ItemStack Components が異なるアイテムを混ぜない
- 保存/再読込で template/count/autoCollect が維持される
- GUI IN/OUT
- 自動回収が空箱を勝手に初期化しない
- 一括収納
- チェスト等への一括排出
- 直接使用の消費数/返却物
- blacklist
- C2S処理の手持ち再検査
- 既存 `gradle build` と dedicated server smoke test

## Non-goals

- 他の StorageBox Mod の保存データとの互換移行
- 元Modのコードやテクスチャのコピー
- Storage Box 内に複数種類を保存
- 容量を Integer.MAX_VALUE より増やす
- 外部StorageBox Modへの必須依存
