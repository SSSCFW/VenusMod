# 王の財宝 Implementation Plan / 実施状態

**Goal:** 既存の王の宝物庫から刀を非消費で展開し、一斉射出する王の財宝を追加する。
**Architecture:** サーバー管理の投影と寿命を、クライアントの描画・入力から分離する。原本はSavedDataに残す。
**Tech Stack:** Java21、Minecraft1.21.1、既存NeoForge構成。
**Spec:** `docs/superpowers/specs/2026-09-17-kings-treasure-design.md`

## 制約

既存feature/venus-hostile-mobsのみ。main・workflow・既存宝物庫の保存形式・無関係なレシピを変更しない。
SlashBladeとCreateの任意依存を維持。成功した検査と未実行の検査を分ける。

## 実施済み

- [x] 基準コミットの元VenusMod.java / build.gradleを読み、Git blob SHAを照合。
- [x] 選択・上限・期限・二重発射の失敗テストを先に作り、独立ロジックを実装。
- [x] キー長押しの失敗チェックを追加し、PressLatchを実装。計20チェック成功。
- [x] UUIDごとの展開状態、server-side入力処理、投影Entityと衝突をソース実装。
- [x] 描画・入力をCLIENT限定クラスに分離。
- [x] アイコン・レシピ・ダメージ種別を生成し、独立実行を検査。
- [x] 5件のGameTestを定義。ただし未実行。
- [x] Java9ファイルを構文解析。Minecraftの依存型チェックは未実行。

## 保留・未達

- [ ] 実依存関係でのGradle check / build。
- [ ] 新規5件と既存GameTest、CreateありGameTest。
- [ ] 専用サーバー、実SlashBlade、マルチプレイ、画面の検証。
- [x] 既存ブランチへ反映する内容を確定。王の財宝をVenusMod専用クリエイティブタブへ明示追加。
- [ ] 配布JAR作成。

## 再現コマンド

パッチは基準コミットの既存ローカルチェックアウトに適用する。まず変更を確認し、適用検査が失敗した場合は強制適用しない。

```powershell
git switch feature/venus-hostile-mobs
git status --short
git apply --check .\VenusMod-kings-treasure.patch
git apply .\VenusMod-kings-treasure.patch
gradle check
gradle runGameTestServer
gradle runGameTestServer -PvenusTestCreate=true
gradle build
```

Gradle9.2.1とJava21、既存プロジェクトの依存関係を用意した環境で実行する。
構文解析や単体チェックの成功を、上記Gradleコマンドの成功と読み替えない。
