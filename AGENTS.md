# VenusModの開発規約

- Minecraft 1.21.1 / NeoForge。Create互換は6.0.10。無関係な仕様やレシピは変更しない。
- 日本語の仕様・コメント、英語のクラス/変数名を使用する。
- 実装済み、検査済み、ゲーム内検証済みを区別し、未実行のテストを成功と記載しない。

## GitHub運用

- 作業ブランチは既存の `feature/venus-hostile-mobs` のみを使用する。検証用・一時用・アセット用などの追加ブランチを作成しない。
- GitHub Actions は既存の `.github/workflows/build-1.21.1.yml`（表示名 `Build 1.21.1 NeoForge`）のみを使用する。別のworkflowファイルを追加しない。
- 一時コミットが必要でも新規ブランチを作らず、`feature/venus-hostile-mobs` 上で通常のコミットとして行う。
- `main` はリポジトリのデフォルトブランチとして既存のまま扱い、通常の開発作業では直接変更しない。

## 機械の隣接面消失を再発させない

1. 新規Create機械は必ず `VenusMachineBlock` を継承する。`KineticBlock` の直接継承は禁止。
2. 基底クラスの `noOcclusion()` とfinalの空遮蔽形状を変更しない。衝突形状を遮蔽形状として流用しない。
3. 全機械を `data/venusmod/tags/block/machines.json` に追加する。Createは任意なのでタグの各エントリは `required:false`。
4. モデルは全頂点を0〜16に収める。隙間のある機械の面に `cullface` を付けない。
5. `gradle check` の描画契約検査と `gradle runGameTestServer -PvenusTestCreate=true` を必須とする。
6. GameTestは全機械・全BlockState・隣接6方向に土/草/石/ガラス/葉を置き、Minecraftの面表示判定を確認する。
7. シェーダーや描画最適化Modによる最終表示は別途クライアントで確認する。ロジック試験だけで全描画環境の無不具合を保証しない。

## 進行仕様

金星の環境、素材、工業、ボスは段階を分けて追加する。未実装を完成扱いしない。
前セッションで確認できない数値は初期バランスとして仕様書に明記し、環境効果はサーバー設定で調整可能にする。
既存ワールドやプレイヤー建築を一括削除・強制再生成しない。

## 抜刀剣の金星強化

- 強化値はSlashBlade本体の内部NBT/Componentを直接改造せず、VenusModのCUSTOM_DATA名前空間に保存する。
- 強化項目は個別レベル制で、上限を越えて加算しない。斬撃/幻影剣はLv10、集中/耐久/金星特効はLv5。
- 各レベルの消費素材合計は必ず前レベルより増える。ボス進行キー（金星核・金星魂石）は所持条件だけに使い、消費しない。
- 金星刀鍛錬機は `VenusMachineBlock` を継承し、隣接面描画の回帰検査対象に含める。
- 金星強化のダメージ・集中・耐久効果はSlashBlade未導入時にクラスリンクしない実装を維持する。
