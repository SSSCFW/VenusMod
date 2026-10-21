# 静的経路GUI Implementation Plan

> superpowers:executing-plans。承認済みNETWORK_SYSTEM_SPECのGUI・静的経路・JEI説明を継続する。

**Goal:** ルーター/L3SWから静的経路の追加と確認付き個別削除を行う。
**Architecture:** 純Javaの経路正規化・重複防止・下書き/確認状態を、既存CLI/サーバー認可へ接続する。GUIはモーダルで下書きを保持し、確認だけをリサイズや編集時に破棄する。
**Tech Stack:** Java21 / Minecraft1.21.1 / NeoForge21.1.235 / optional JEI19.21.0.247。
**Spec:** docs/NETWORK_SYSTEM_SPEC.md、承認済みネットワーク実装の継続依頼。

## 制約とレビュー
- feature/venus-hostile-mobsと既存Build 1.21.1 NeoForgeのみ。新規ブランチ・workflowなし。
- 保存v16・network-3・136種4/16幅ケーブル・画像/モデル/レシピを維持。
- 個別削除は正規化した宛先/長さ/次ホップ/ポートの一致だけ。同じ意味の旧重複はまとめて除去する。
- 重複追加・未存在削除・誤入力は通信/NATを再初期化しない。実際の経路変更は従来の再確立ルール。
- 設定一覧と疎通成功を区別し、戻り経路も必要と説明。削除確認を表示しただけでは送信しない。
- GUIの裏のJEI・Inventoryへ操作を渡さず、実入力とサーバー状態を検査する。

## Tasks
- [x] 1. 中断したtreeの11ファイルを回収。変更前に個別削除失敗と下書きAPI未実装を確認。
- [x] 2. 純Java139検査と全103テストクラスを実行。追加/削除/正規化/認可/保存/通信を検査。
- [ ] 3. 実配線GameTestと実JEIの経路フォーム試験を既存検証へ組み込む。全体build/check。
- [ ] 4. 差分確認、既存ブランチへpush、既存CI・実画面・公開結果を記録。

## Ledger
- 開始live head a5b348c。独立ディレクトリ routes-resume-20261021-bf71a9。未参照tree 59ce7e1から回収。ローカルsnapshotコミットはリモート親にしない。
- 変更前のStaticRouteCommandTestで個別削除未対応を再現し、StaticRouteDraftTestはAPI不足でコンパイル失敗。その後回収したコードを反映。
- StaticRouteCommandTest43検査、StaticRouteDraftTest96検査、全103クラス成功。
- 独立したレビュアーは利用せず、差分自己レビューと回帰試験を行う。
