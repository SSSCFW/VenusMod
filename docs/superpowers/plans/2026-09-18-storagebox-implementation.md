# Storage Box Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a NeoForge 1.21.1 native `venusmod:storage_box` that reproduces the approved StorageBox-style one-item bulk storage workflow without copying external source/assets.

**Architecture:** Keep all Storage Box code under `dev.ssscfw.venusmod.storagebox`. Persistent item state lives in custom Data Components registered by VenusMod; `StorageBoxData` is the only mutation API, `StorageBoxRules` holds Minecraft-independent arithmetic, and item/menu/events/network/client code call those APIs. Server owns every mutation; client only handles key input, screen rendering, and item decoration.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.x, VenusMod existing DeferredRegister/event/network patterns.

**Spec:** `docs/superpowers/specs/2026-09-18-storagebox-design.md`

## Global Constraints

- Stay on existing branch `feature/venus-hostile-mobs`; do not create additional branches or workflows.
- Do not copy code or textures from external StorageBox projects; use only observed behavior.
- No required Create or SlashBlade dependency.
- Capacity is exactly `Integer.MAX_VALUE`.
- Stored item equality uses full ItemStack components.
- Storage Box itself and items in `venusmod:storage_box_blacklist` are rejected.
- All mutations happen server-side through `StorageBoxData`.
- Preserve unrelated VenusMod behavior and existing build workflow.

---

### Task 1: Core rules and data components

**Files:**
- Create: `src/main/java/dev/ssscfw/venusmod/storagebox/StorageBoxRules.java`
- Create: `src/main/java/dev/ssscfw/venusmod/storagebox/StorageBoxData.java`
- Create: `src/main/java/dev/ssscfw/venusmod/registry/ModDataComponents.java`
- Modify: `src/main/java/dev/ssscfw/venusmod/VenusMod.java`
- Test: `tools/tests/StorageBoxRulesChecks.java`
- Modify: `build.gradle`
- Create: `gradle/storage-box-verification.gradle`

**Interfaces:**
- Produces: `StorageBoxRules.accepted(int stored, int requested)`, `extractable(int stored, int requested, int maxStack)`, `largeChestEquivalent(int stored, int maxStack)`.
- Produces: `StorageBoxData.template(ItemStack)`, `storedCount(ItemStack)`, `autoCollect(ItemStack)`, `insert(ItemStack, ItemStack, int)`, `extract(ItemStack, int)`, `toggleAutoCollect(ItemStack)`, `canAccept(ItemStack, ItemStack)`.

- [ ] Write `StorageBoxRulesChecks` with capacity/overflow/extraction/LC tests.
- [ ] Run the pure Java check and verify it fails because `StorageBoxRules` is missing.
- [ ] Add `ModDataComponents` with persistent/network-synchronized template, count and auto-collect components.
- [ ] Implement `StorageBoxRules` and `StorageBoxData`.
- [ ] Add `gradle/storage-box-verification.gradle` and wire it into `build.gradle`.
- [ ] Re-run the pure Java check and source-contract checks.

### Task 2: Item registration, blacklist, recipe, translations and original assets

**Files:**
- Create: `src/main/java/dev/ssscfw/venusmod/storagebox/StorageBoxItem.java`
- Modify: `src/main/java/dev/ssscfw/venusmod/registry/ModItems.java`
- Modify: `src/main/resources/assets/venusmod/lang/ja_jp.json`
- Modify: `src/main/resources/assets/venusmod/lang/en_us.json`
- Create: `src/main/resources/data/venusmod/tags/item/storage_box_blacklist.json`
- Create: `src/main/resources/data/venusmod/recipe/storage_box.json`
- Modify: `tools/java/VenusAssetGenerator.java`

**Interfaces:**
- Produces: `ModItems.STORAGE_BOX`.
- `StorageBoxItem` opens GUI in air and delegates stored-item direct-use through `StorageBoxUseHandler`.
- Tooltip reads only `StorageBoxData`.

- [ ] Add a source-contract test proving registration, recipe, blacklist and translations are present.
- [ ] Register the item with max stack size 1 and add it to Tools & Utilities creative tab.
- [ ] Add chest-ring recipe and blacklist tag containing Storage Box and vanilla recursive storage items.
- [ ] Extend the existing Java asset generator with an original Storage Box icon/badge; do not copy external assets.
- [ ] Add Japanese/English strings and tooltip text.

### Task 3: Server menu and client screen

**Files:**
- Create: `src/main/java/dev/ssscfw/venusmod/storagebox/StorageBoxMenu.java`
- Create: `src/main/java/dev/ssscfw/venusmod/client/StorageBoxScreen.java`
- Modify: `src/main/java/dev/ssscfw/venusmod/registry/ModMenus.java`
- Modify: `src/main/java/dev/ssscfw/venusmod/client/VenusPhase2Client.java`

**Interfaces:**
- Menu binds to the exact main-hand Storage Box instance and exposes synchronized count/auto-collect/template.
- Button IDs: `BUTTON_AUTO_COLLECT=0`.
- Virtual IN slot immediately inserts carried matching items.
- Virtual OUT slot extracts up to template max stack size.

- [ ] Add source-contract tests for menu registration and main-hand validity.
- [ ] Implement server-authoritative IN/OUT behavior and safe rollback if inventory cannot accept extracted output.
- [ ] Implement screen with template icon, item name, count/capacity, LC equivalent, IN/OUT labels, auto-collect button and player inventory.
- [ ] Register screen through `RegisterMenuScreensEvent`.

### Task 4: Key controls and C2S actions

**Files:**
- Create: `src/main/java/dev/ssscfw/venusmod/storagebox/StorageBoxNetwork.java`
- Create: `src/main/java/dev/ssscfw/venusmod/client/StorageBoxClient.java`
- Modify: `src/main/java/dev/ssscfw/venusmod/VenusMod.java`
- Modify: `src/main/java/dev/ssscfw/venusmod/client/VenusPhase2Client.java`

**Interfaces:**
- `StorageBoxNetwork.Action`: `INSERT_ALL`, `EXTRACT_STACK`, `DROP_STACK`, `TOGGLE_AUTO_COLLECT`.
- Client key: apostrophe key; modifier state determines action.
- Server validates alive/non-spectator/main-hand Storage Box before mutation.

- [ ] Add source-contract test that client sends only enum action and server revalidates main hand.
- [ ] Register key mapping and client tick input.
- [ ] Register payload codec/handler.
- [ ] Implement inventory-wide insert, one-stack extract, one-stack drop, auto-collect toggle.

### Task 5: Auto-collect and block-inventory dump

**Files:**
- Create: `src/main/java/dev/ssscfw/venusmod/storagebox/StorageBoxEvents.java`
- Modify: `src/main/java/dev/ssscfw/venusmod/VenusMod.java`
- Modify: `src/main/java/dev/ssscfw/venusmod/storagebox/StorageBoxItem.java`

**Interfaces:**
- Pickup event scans hotbar then main inventory.
- Auto-collect never initializes an empty box.
- `dumpIntoBlockInventory(ServerPlayer, InteractionHand, BlockPos, Direction)` uses `Capabilities.ItemHandler.BLOCK`.

- [ ] Add a pure/source test that empty boxes are skipped during auto-collect.
- [ ] Implement pickup interception with partial remainder support.
- [ ] Implement right-click-on-container dump using simulated/real `IItemHandler.insertItem` passes.
- [ ] Ensure remaining count stays in box and no item duplication occurs.

### Task 6: Stored-item direct use

**Files:**
- Create: `src/main/java/dev/ssscfw/venusmod/storagebox/StorageBoxUseHandler.java`
- Modify: `src/main/java/dev/ssscfw/venusmod/storagebox/StorageBoxItem.java`

**Interfaces:**
- `useAir`, `useOnBlock`, and `useOnEntity` temporarily substitute at most one normal max-stack of the template, call the underlying item behavior, then reconcile.
- Template-matching remainder returns to box.
- Changed/byproduct stacks go to player inventory or owned drop.

- [ ] Add source contracts covering temporary hand replacement and restoration.
- [ ] Implement air-use delegation including consumables.
- [ ] Implement block and entity delegation.
- [ ] Reconcile count delta and byproducts without merging different Components into the box.
- [ ] Explicitly reject nesting Storage Box inside Storage Box.

### Task 7: Item visuals and slot decoration

**Files:**
- Create: `src/main/java/dev/ssscfw/venusmod/client/StorageBoxItemDecorator.java`
- Modify: `src/main/java/dev/ssscfw/venusmod/client/VenusPhase2Client.java`
- Modify: `src/main/java/dev/ssscfw/venusmod/storagebox/StorageBoxItem.java`

**Interfaces:**
- Non-empty box reports foil/glint.
- Item decorator renders stored template icon and a small original VenusMod box badge in GUI slots.
- Empty box uses the generated base icon.

- [ ] Register `IItemDecorator` using `RegisterItemDecorationsEvent`.
- [ ] Render the stored template in the Storage Box slot without recursively decorating nested boxes.
- [ ] Render the badge and compact count suffix.
- [ ] Add rendering source checks.

### Task 8: GameTests, docs, and final verification

**Files:**
- Create: `src/main/java/dev/ssscfw/venusmod/test/StorageBoxGameTests.java`
- Modify: `gradle/storage-box-verification.gradle`
- Modify: `docs/superpowers/specs/2026-09-18-storagebox-design.md` only if implementation findings require a documented correction.

**Interfaces:**
- GameTests exercise component equality, menu insert/extract, blacklist, auto-collect policy and serialization.

- [ ] Add GameTests for full-component equality and persistence.
- [ ] Add GameTests for empty-box auto-collect rejection and registered-template auto-collect.
- [ ] Add GameTests for extraction and Integer.MAX_VALUE capacity edge.
- [ ] Run all pure Java verification tasks.
- [ ] Run `gradle build --build-cache --stacktrace` when an environment with dependency access is available.
- [ ] Run dedicated server smoke test when available.
- [ ] Review final diff against the design and confirm no external StorageBox code/assets were copied.
- [ ] Commit and push to `feature/venus-hostile-mobs`.
