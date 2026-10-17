# VenusMod

Minecraft 1.21.1 / NeoForge starter template.

## Environment

- Minecraft 1.21.1
- NeoForge 21.1.x (`21.1.235` pinned initially)
- Java 21
- Gradle 9.2.1

## Project identifiers

- Mod ID: `venusmod`
- Java package: `dev.ssscfw.venusmod`
- Main class: `dev.ssscfw.venusmod.VenusMod`

Version, NeoForge version, mod ID/name and group are managed in `gradle.properties`.

## Local development

Build:

```bash
gradle build
```

Run client:

```bash
gradle runClient
```

Run dedicated server:

```bash
gradle runServer
```

Run data generation:

```bash
gradle runData
```

Build output is written to `build/libs/`.

## GitHub Actions

The `Build 1.21.1 NeoForge` workflow automatically builds and publishes the mod after each push to `feature/venus-hostile-mobs` or `main` (after the workflow change is merged to `main`). Manual runs are also available. Automatic push runs build the JAR; the optional dedicated-server smoke test is selected for manual runs.

To run it manually:

1. Open **Actions**.
2. Select **Build 1.21.1 NeoForge**.
3. Click **Run workflow**.
4. Select the branch to build.
5. Enable **Run dedicated server smoke test** when registry/data-pack/server startup validation is needed.

The workflow uses Java 21, Gradle 9.2.1, Gradle/configuration build caches, and optionally starts a dedicated NeoForge server until it reaches the ready state.

On success, the built mod is published to the rolling prerelease `ci-latest` as `VenusMod-1.21.1.jar`, so it can be downloaded directly as a JAR rather than a ZIP artifact.

Direct JAR URL after the first successful build:

`https://github.com/SSSCFW/VenusMod/releases/download/ci-latest/VenusMod-1.21.1.jar`

## Suggested source layout

```text
src/main/java/dev/ssscfw/venusmod/
  VenusMod.java
  client/
  config/
  event/
  item/
  network/
  registry/

src/main/resources/
  META-INF/neoforge.mods.toml
  assets/venusmod/
  data/venusmod/
```

Add registries and mod-bus listeners from `VenusMod.java` as features are implemented.

## ボス施設と報酬

金星将軍の城塞・アフロディーテ神殿の構成、探索コマンド、既存構造物の壁修正、王の財宝と王の宝物庫の入手方法は [ボス施設の仕様](docs/BOSS_STRUCTURES.md) を参照してください。

## 閻魔刀の次元移動

魔剣「閻魔刀」を持ってHキーで、地点保存・フォルダー整理・転送条件を設定できる「次元移動」を開けます。操作と隙間の設定は [閻魔刀の次元移動](docs/YAMATO_DIMENSION_TRAVEL.md) を参照してください。
