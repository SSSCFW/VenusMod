# 閻魔刀の次元移動 実装計画

> Agentic workers: use the supplied interfaces and independently review each component before integration.

**Goal:** 閻魔刀専用の整理可能な転送先GUIと双方向の次元の隙間を実装する。

**Architecture:** 保存情報・サーバー転送処理・クライアントGUI/描画を分離する。サーバーが所有権と座標を検証し、同じ対から両端を生成する。

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.235, optional SlashBlade Resharped.

**Spec:** `docs/superpowers/specs/2026-10-17-yamato-dimension-travel-design.md`

## 制約と確認点

既存作業ブランチと既存workflowのみを使用する。任意依存・専用サーバー起動を維持し、無関係な変更は行わない。重点は階層循環、到着後の再ログイン、未読込次元、同時通過と乗客、1/180秒境界。

## タスク

- [ ] `yamato/YamatoTravelData.java`: `get(MinecraftServer)`, `snapshot(UUID)`, `settings(UUID)`, `destination(UUID,UUID)`, `apply(ServerPlayer,String,CompoundTag)`。先に階層・並び替え・入力拒否の純粋テストを作り、SavedDataのGameTestを追加する。
- [ ] `client/YamatoTravelClient.java`, `YamatoTravelScreen.java`: 設定可能Hキー、左側設定、中央一覧、フォルダー、確認付き削除、開放対管理。320×240以上の配置を純粋テストで検証する。
- [ ] `compat/YamatoCompat.java`, `client/YamatoCutAnimation.java`, `YamatoRiftRenderer.java`: 実刀ID判定、無害な二段モーション、End Gateway描画。native APIを原典と照合する。
- [ ] `yamato/YamatoRiftGeometry.java`, `YamatoRiftRules.java`, `YamatoRifts.java`: 先に曲線、寿命、到着ロックの規則テストを作成し、対・接触・転送と永続化を実装する。
- [ ] `yamato/YamatoNetwork.java`, `client/YamatoRiftsClient.java`: 小さい操作要求とサーバー状態通知、実刀・所有権検証、サーバーtickと描画状態の同期を接続する。
- [ ] 既存初期化へ登録し日本語/英語の翻訳を追加する。`gradle build` と `gradle runGameTestServer -PvenusTestCreate=true` を実行、独立レビュー後に作業ブランチへ反映しActionsを確認する。
