# King Treasury Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a player-bound 10,000-blade King Treasury with 1,028-count logical stacking, a paged 54-slot GUI, and inventory-full-safe automatic SlashBlade pickup.

**Architecture:** Store actual blades in Overworld `SavedData` keyed by player UUID. Each logical entry stores one representative `ItemStack` plus an integer count; identical items use `ItemStack.isSameItemSameComponents`, never by increasing Minecraft's real stack limit. The menu only synchronizes the current 54-entry page, while pickup interception writes directly to SavedData before vanilla inventory insertion.

**Tech Stack:** Minecraft 1.21.1, NeoForge 21.1.x, existing optional SlashBlade reflection bridge, vanilla `SavedData`, `AbstractContainerMenu`, NeoForge `ItemEntityPickupEvent.Pre`.

**Spec:** `docs/superpowers/specs/2026-09-17-kings-treasury-design.md`

## Global Constraints

- Work only on existing branch `feature/venus-hostile-mobs`.
- Do not create any new branch.
- Keep `.github/workflows/build-1.21.1.yml` as the only workflow; do not add workflow files.
- SlashBlade remains optional and VenusMod must not hard-link SlashBlade classes.
- Capacity is exactly 10,000 blades per player.
- A logical stack is capped at exactly 1,028 blades.
- Logical stacking requires item + all components to match exactly.
- Inventory-full automatic pickup must not delete blades if storage fails.

---

### Task 1: Storage rules and regression tests

**Files:**
- Create: `src/main/java/dev/ssscfw/venusmod/treasury/TreasuryRules.java`
- Create: `tests/TreasuryRulesTest.java`

**Interfaces:**
- Produces: `TreasuryRules.MAX_TOTAL = 10000`, `TreasuryRules.MAX_LOGICAL_STACK = 1028`, `acceptedIntoStack(int currentCount, int requested, int remainingTotalCapacity)`.

- [ ] **Step 1: Write the failing pure-Java test**

```java
import dev.ssscfw.venusmod.treasury.TreasuryRules;

public final class TreasuryRulesTest {
    public static void main(String[] args) {
        require(TreasuryRules.acceptedIntoStack(0, 2000, 10000) == 1028, "logical stack cap");
        require(TreasuryRules.acceptedIntoStack(1000, 100, 10000) == 28, "fill existing stack");
        require(TreasuryRules.acceptedIntoStack(0, 100, 37) == 37, "global remaining capacity");
        require(TreasuryRules.acceptedIntoStack(1028, 1, 9999) == 0, "full stack rejects");
        require(TreasuryRules.MAX_TOTAL == 10000, "total cap");
        require(TreasuryRules.MAX_LOGICAL_STACK == 1028, "logical cap");
        System.out.println("TreasuryRulesTest passed");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```sh
mkdir -p build/standalone-tests
javac -d build/standalone-tests src/main/java/dev/ssscfw/venusmod/treasury/TreasuryRules.java tests/TreasuryRulesTest.java
```

Expected: compile failure because `TreasuryRules` does not exist yet.

- [ ] **Step 3: Implement minimal rules**

`TreasuryRules.acceptedIntoStack` returns `min(requested, MAX_LOGICAL_STACK-currentCount, remainingTotalCapacity)` with all negative inputs clamped to zero.

- [ ] **Step 4: Run test and verify GREEN**

Run the same `javac`, then:

```sh
java -cp build/standalone-tests TreasuryRulesTest
```

Expected: `TreasuryRulesTest passed`.

- [ ] **Step 5: Commit**

Commit message: `test: define King Treasury capacity rules`

---

### Task 2: Player-bound SavedData storage

**Files:**
- Create: `src/main/java/dev/ssscfw/venusmod/treasury/KingsTreasurySavedData.java`
- Create: `src/main/java/dev/ssscfw/venusmod/treasury/TreasuryEntry.java`
- Modify: `src/main/java/dev/ssscfw/venusmod/test/VenusGameTests.java`

**Interfaces:**
- Consumes: `SlashBladeEnchantmentCompat.isBlade(ItemStack)`, `TreasuryRules` constants.
- Produces:
  - `KingsTreasurySavedData.get(ServerLevel level)`
  - `PlayerTreasury get(UUID playerId)`
  - `int insert(UUID playerId, ItemStack stack, int requested)` returning accepted blade count
  - `ItemStack extractOne(UUID playerId, int entryIndex)`
  - `List<TreasuryEntry> page(UUID playerId, int page, int pageSize)`
  - `int totalCount(UUID playerId)`
  - `boolean autoCollect(UUID playerId)` / `boolean toggleAutoCollect(UUID playerId)`

- [ ] **Step 1: Add a GameTest that fails before storage exists**

Test creates two player UUIDs, inserts component-identical SlashBlade stacks into one player and verifies: same components merge to count 1028; overflow creates another logical entry; a second UUID remains empty; total insertion clamps at 10,000; modified components do not merge.

- [ ] **Step 2: Run existing GameTest workflow locally if available, otherwise compile is expected to fail in CI until implementation exists**

Expected failure: missing `KingsTreasurySavedData` / `TreasuryEntry`.

- [ ] **Step 3: Implement `TreasuryEntry` and SavedData**

Use `ItemStack.isSameItemSameComponents(template, incoming)` for equality. Always store template copies with count 1. Serialize each player as UUID + `AutoCollect` + entry list (`Stack`, `Count`). Use the repository's existing `VenusPortalLinks` SavedData factory pattern and ItemStack's registry-aware NBT codec/save API for 1.21.1.

- [ ] **Step 4: Add defensive load validation**

Discard empty/non-SlashBlade templates, clamp each count to 1..1028, and stop reading once 10,000 total blades have been reconstructed for a player.

- [ ] **Step 5: Run tests and verify GREEN**

Run pure-Java rules test and the existing GameTest suite through the existing `Build 1.21.1 NeoForge` workflow when implementation is complete.

- [ ] **Step 6: Commit**

Commit message: `feat: add player-bound King Treasury storage`

---

### Task 3: King Treasury item and menu registration

**Files:**
- Create: `src/main/java/dev/ssscfw/venusmod/item/KingTreasuryItem.java`
- Create: `src/main/java/dev/ssscfw/venusmod/treasury/KingsTreasuryMenu.java`
- Create: `src/main/java/dev/ssscfw/venusmod/registry/ModMenus.java`
- Modify: `src/main/java/dev/ssscfw/venusmod/registry/ModItems.java`
- Modify: `src/main/java/dev/ssscfw/venusmod/VenusMod.java`
- Modify: `src/main/java/dev/ssscfw/venusmod/registry/ModCreativeTabs.java`

**Interfaces:**
- Produces item id `venusmod:king_treasury` and menu id `venusmod:king_treasury`.
- `KingTreasuryItem.use(Level, Player, InteractionHand)` opens GUI normally and toggles auto-collect when crouching.
- `KingsTreasuryMenu` exposes 54 read-only treasury display slots plus normal player inventory slots, current page, total count, and logical counts for each displayed treasury slot.

- [ ] **Step 1: Add compile-time references in GameTest/registry test before production registration**

Assert the item and menu registry holders exist and that the item is present in the VenusMod creative tab path.

- [ ] **Step 2: Verify RED**

Expected: missing `KING_TREASURY` / `KING_TREASURY_MENU` symbols.

- [ ] **Step 3: Register the menu type**

Use `DeferredRegister<MenuType<?>>` and `IMenuTypeExtension.create` so the client constructor receives the opening player's current page data context. Register `ModMenus.MENUS` from `VenusMod` unconditionally; SlashBlade remains optional.

- [ ] **Step 4: Register `KingTreasuryItem`**

The item is non-stackable (`stacksTo(1)`). Normal right click uses `ServerPlayer.openMenu`; crouch-right-click toggles SavedData and sends an action-bar translatable message without opening the GUI.

- [ ] **Step 5: Implement `KingsTreasuryMenu` server authority**

Treasury slots are virtual and may not accept vanilla placement. Override menu click handling for slots 0..53:

- Empty cursor + left click on occupied treasury slot: extract exactly one blade to cursor.
- Shift-click occupied treasury slot: extract one only if player inventory accepts the returned blade; otherwise leave storage unchanged.
- Shift-click a player inventory SlashBlade slot: insert as much as storage permits and shrink the player stack only by the accepted amount.
- Ignore non-SlashBlade deposits.
- Previous/next page are handled through `clickMenuButton` IDs.

After every mutation, rebuild only the current 54 representative stacks and logical counts and call `broadcastChanges()`.

- [ ] **Step 6: Verify tests GREEN and commit**

Commit message: `feat: add King Treasury item and server menu`

---

### Task 4: Client backpack screen

**Files:**
- Create: `src/main/java/dev/ssscfw/venusmod/client/KingsTreasuryScreen.java`
- Modify: `src/main/java/dev/ssscfw/venusmod/client/VenusPhase2Client.java` or add a focused client registry subscriber if that keeps optional Create references isolated.
- Create: `src/main/resources/assets/venusmod/models/item/king_treasury.json`
- Create: `src/main/resources/assets/venusmod/textures/item/king_treasury.png`
- Modify: `src/main/resources/assets/venusmod/lang/ja_jp.json`
- Modify: `src/main/resources/assets/venusmod/lang/en_us.json`

**Interfaces:**
- Consumes: `KingsTreasuryMenu` current page, page count, total count, 54 logical counts.
- Produces: a 9x6 treasure grid with previous/next buttons and custom `x<count>` labels rendered over representative blade icons.

- [ ] **Step 1: Add resource-validation assertions first**

Modify `tools/validate_venus_resources.py` to require the item model, 16x16 PNG, Japanese/English item name, auto-collect ON/OFF messages, page/title labels, and no new workflow files.

- [ ] **Step 2: Run validator and verify RED**

Expected: missing King Treasury resources.

- [ ] **Step 3: Implement screen and screen registration**

Use `AbstractContainerScreen<KingsTreasuryMenu>`. Render 54 storage slots, player inventory, page indicator, total `N / 10000`, previous/next buttons, and logical counts up to 1028 independently from the representative ItemStack count.

- [ ] **Step 4: Add item icon and translations**

Use a gold/black treasury motif. Do not alter SlashBlade textures or bundled assets.

- [ ] **Step 5: Run validator and compile tests GREEN**

Expected: static validator passes.

- [ ] **Step 6: Commit**

Commit message: `feat: add King Treasury backpack screen`

---

### Task 5: Inventory-full-safe automatic pickup

**Files:**
- Create: `src/main/java/dev/ssscfw/venusmod/event/KingsTreasuryEvents.java`
- Modify: `src/main/java/dev/ssscfw/venusmod/VenusMod.java`
- Modify: `src/main/java/dev/ssscfw/venusmod/test/VenusGameTests.java`

**Interfaces:**
- Consumes: `ItemEntityPickupEvent.Pre`, `KingsTreasurySavedData.insert`, `ModItems.KING_TREASURY`, `SlashBladeEnchantmentCompat.isBlade`.
- Produces: direct pickup into treasury when enabled, even if normal player inventory has no room.

- [ ] **Step 1: Write failing GameTest for full-inventory pickup**

Create a server player with all inventory slots occupied, give them a King Treasury item, enable auto-collect, spawn a SlashBlade ItemEntity in pickup range, then invoke/allow pickup processing and assert the blade enters SavedData while player inventory stays full.

- [ ] **Step 2: Verify RED**

Expected: dropped blade remains because no pickup handler exists.

- [ ] **Step 3: Implement `ItemEntityPickupEvent.Pre` handler**

Return immediately unless server player, auto-collect enabled, player inventory contains `KING_TREASURY`, and ItemEntity stack is a SlashBlade. Insert the live stack count into SavedData first. Only after a positive accepted count, shrink the live ItemEntity stack by that exact amount. If it becomes empty, call `player.take(itemEntity, accepted)` then discard it. Set `event.setCanPickup(TriState.FALSE)` after any direct-storage insertion so vanilla cannot duplicate the handled blades.

- [ ] **Step 4: Handle partial/full storage safely**

If accepted is zero, leave event untouched so vanilla pickup can proceed normally. If accepted is partial, keep the remainder on the ground and set pickup false for this collision. Never discard before storage mutation succeeds.

- [ ] **Step 5: Run GameTest GREEN and commit**

Commit message: `feat: auto-collect SlashBlades into King Treasury`

---

### Task 6: Final regression verification

**Files:**
- Modify only if tests reveal a defect.

**Interfaces:** None.

- [ ] **Step 1: Run pure-Java tests**

```sh
mkdir -p build/standalone-tests
javac -d build/standalone-tests src/main/java/dev/ssscfw/venusmod/treasury/TreasuryRules.java tests/TreasuryRulesTest.java
java -cp build/standalone-tests TreasuryRulesTest
```

Expected: PASS.

- [ ] **Step 2: Run resource validator**

```sh
python3 tools/validate_venus_resources.py
```

Expected: PASS.

- [ ] **Step 3: Verify GitHub topology**

Confirm branches remain only `main` and `feature/venus-hostile-mobs`, and `.github/workflows` still contains only `build-1.21.1.yml`.

- [ ] **Step 4: Run existing `Build 1.21.1 NeoForge` workflow**

Use only the existing workflow. Do not modify its triggers or create a temporary workflow. Confirm Gradle build and optional requested smoke test results before claiming success.

- [ ] **Step 5: Commit any verification-only fixes on `feature/venus-hostile-mobs`**

No new branch, no workflow changes.
