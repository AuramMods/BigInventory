# BigInventory implementation and QA plan

BigInventory replaces the player's 27 backpack slots with searchable storage limited by weight. The nine hotbar slots keep their vanilla indices, selection and stack limits. Armor, offhand, equipped Curios, the crafting grid and the carried cursor stack stay separate. The target is survival play on both integrated and dedicated Minecraft 1.20.1 servers running Auram's Forge 47.4.10.

## Capacity contract

Use 500 as the default base capacity. A pack author can set it to 512 or another value in the per-world Forge SERVER config. Set the integer footprint factor to 32 by default and accept 1 through 8192 inclusive. Reserve signed, configurable capacity adjustments for equipped Curios. The server owns these values.

For each stored item, read the actual stack's maximum size, including mod overrides. For positive integer stack sizes, compute `1 + (factor - 1) / max(1, stackMax)` with integer division. Multiply by the number of individual items. Accumulate in a wide integer. Clamp effective capacity (base plus all equipped adjustments) to at least zero.

| Max stack size | Factor 16 | Default 32 | Factor 64 |
| ---: | ---: | ---: | ---: |
| 64 | 1 | 1 | 1 |
| 16 | 1 | 2 | 4 |
| 1 | 16 | 32 | 64 |

At defaults, 500 cobblestone fill the store. So do 500 distinct one-unit items, or 15 tools and 20 cobblestone. At usage 499, an incoming stack of cobblestone contributes exactly one item. A bucket with stack size 16 contributes none at that point. Return the exact remainder to the caller. Hotbar contents consume no backpack capacity.

## Storage and vanilla integration

1. **Own each item once.** Attach a versioned, serializable store to the server player. Preserve registry identity, full stack NBT and serialized Forge capabilities. Keep mutable stacks in legal sizes for vanilla operations and ticking. Group mutually stack-compatible instances for display and sum their counts independently of physical stack size. Damage, enchantments, names, contents and capability state can distinguish two instances of the same item.
2. **Centralize transfers.** Give pickups, menus and automation the same simulate/insert/extract operations. Simulations leave state untouched. Mutations re-evaluate remaining capacity on the server and return the accepted quantity or remainder. Multi-endpoint transfers must account for both sides and the cursor even on partial acceptance, disconnect or menu close. Define explicit handling for creative pickup, clone, trash and commands.
3. **Bridge the vanilla inventory.** Keep fixed hotbar, armor and offhand indices valid. Trace `Inventory`, player-menu slots, `Inventory.add`, Forge's player item handlers, recipe lookup/consumption and quick-move paths before choosing hooks. Prototype a compatibility view of real stored stacks for indexed readers/writers and a deposit path for empty slots. Keep slot references stable for each open menu. A vanilla chest, crafting table or modded machine must still let a player deposit and withdraw. Test direct list access and hard-coded 9–35 scans, including items beyond the first 27 types. Document per-mod adapters where those callers cannot traverse the full store.
4. **Tick stored items.** Deliver normal inventory ticks to physical stacks exactly once per tick, with an unselected slot context. Reconcile count, NBT, capability, stack-size and identity changes after a tick or external mutation. Test tools that use energy, repair, charge nearby items, transform themselves or consume a different stack. Preserve newly overweight holdings until the player can unload them.
5. **Migrate and persist.** When an existing player first loads, import storage slots 9–35 in the same persisted player state that records the schema version and clears the originals. Import all holdings even when already overweight. Test a second login, save/restart, dimension travel and End return for repeated import or lost ownership. On ordinary death, hand legal-size stacks to the normal drops pipeline once. Respect keepInventory and Forge death/drop handling. Copy retained storage exactly once on respawn. Exercise both settings and Auram's keep-inventory mod separately.

Prefer Forge hooks where they own the whole operation. Introduce narrowly scoped mixins for verified gaps such as vanilla insertion or fixed-size iteration. For each injection record the mapped target, bypass paths, known interactions and a regression case. Exercise the reobfuscated production jar in a separate pack copy as well as the mapped dev environment.

## Curios and encumbrance

Load the integration only when Curios is installed. Define server-side signed modifier rules keyed by equipped item ID and optionally slot ID. A physical equipped item contributes its configured amount once. Ignore cosmetic and unequipped copies. Start with an empty rule set so pack authors choose which accessories grant or remove capacity. Recalculate on equip, unequip, login, respawn and config changes, including removal of a negative modifier.

When used weight exceeds effective capacity, preserve every item. Block additional positive-weight deposits, allow withdrawals, and continue to allow equipment changes that restore capacity. Apply a stable movement-speed multiplier of 0.5 and Mining Fatigue I (initial interpretation of fatigue). At or below capacity, clear BigInventory's penalty promptly. Avoid stacking speed modifiers or removing another mod's fatigue. Verify sprinting, relogging, rapid swaps, zero capacity and a server config change while the menu is open.

## Inventory screen, HUD and networking

- Keep the vanilla equipment, offhand, player preview, crafting controls and pinned nine-slot hotbar usable. Replace backpack positioning with an aggregated list. Click to take a legal stack, right-click to take a smaller amount, and deposit from the cursor or by quick-move. The list order is stable while interacting. Drag, sort and number-key actions must never rearrange virtual entries or duplicate their contents.
- Lay out in scaled GUI coordinates. Reserve header, equipment, hotbar, margins and scrollbar first. Fit complete rows into the remaining height and aim for up to 18 columns with compact slots. Reduce columns on narrow windows, keep click targets aligned with rendered icons, and preserve a usable scrolling viewport at high GUI scale. Handle resizing, JEI panels, recipe book and Curios controls.
- Search by displayed item name and registry ID. Keep filtering separate from storage identity, clamp scroll after filter/resize/removal, support wheel and scrollbar drag, and show full item tooltips and aggregate counts.
- Left of the hotbar show a chest, a ring and the percentage of capacity **remaining**. Clamp the display to 0–100%, including zero capacity. On the right show the total matching count for the selected non-tool item across hotbar and storage, including the held stack once. Suppress it for an empty hand or a tool. Exercise modded tools and unstackable non-tools.
- Send authoritative usage, capacity and held-count changes to the owner. Page large result sets and synchronize only the data needed by the open menu. Address actions by entry identity and menu revision so a moving row cannot select another item. Validate the active menu and available count when handling an action, and refresh stale views on the server. Test latency, concurrent pickups, rapid clicks, close/reopen and disconnect with a carried stack.

## Delivery sequence and evidence

| Milestone | Work and exit evidence |
| --- | --- |
| Project setup (current request) | Commit this plan first. Pin the Forge toolchain to Auram, register BigInventory, configure client/server runs and import exact JEI, Jade and Curios jars from the supplied instance into a local dev profile. Build the production jar and record which launches actually succeeded. |
| Storage first slice | Implement capacity config, stack math, persistence and server transfer operations. Test factor endpoints, odd stack sizes, large counts, full/partial transfers, migration and restart. Compare per-identity totals and full item data before and after each operation. |
| Playable first pass | Wire pickups, vanilla/modded containers, crafting, commands, death/clone, ticks and network sync. Build the searchable screen, HUD and Curios rules. Verify a complete gather → use → refill → unload → die → respawn loop with two players on a dedicated server. |
| Focused integration QA | Exercise the cases below against exact jars and record version, steps, expected/observed counts, logs and screenshots for UI issues. Run both with and without optional integrations. |
| Auram acceptance | Use a disposable instance and world copied from Auram. Test fresh and migrated players, restart mid-session, and compare tick time, allocations, packet volume and screen responsiveness against the same pack/world without BigInventory. Record failures and the affected versions before claiming compatibility. |

### First playable QA matrix

| Scenario | Observable outcome |
| --- | --- |
| 500 cobble, 500 distinct one-unit entries, mixed stacks, attempted pickup at 499/500 | All entries are reachable, only the fitting quantity enters, world stack/cursor and storage totals reconcile. |
| Equal item IDs with different damage, enchantments, names or stored energy | Each withdrawal returns the selected variant with its original data. |
| Chest, machine, crafting/recipe book, JEI recipe transfer, drag/shift-click and cancel | Each transaction consumes or returns exactly the expected counts, including at full capacity. JEI and Jade overlays remain usable. |
| Equip positive/negative Curios, remove boost with a full bag, deposit, unload, re-equip | Capacity changes once, overweight holdings survive, speed halves and fatigue applies, then both recover. |
| Consume the last block or break a tool with a matching spare outside the visible page | Auram's Stack Refill and any chosen Inventory Tweaks equivalent refill the correct hotbar slot and consume exactly one replacement. |
| Passive charging, Inventory Mending and Inventory Totem, including after filtering or scrolling | Stored items remain discoverable, tick once and persist their mutations. |
| Death with drops, death with retention, respawn, End exit, relog and restart | The player plus world drops account for all expected items once. Other players cannot see or operate this store. |
| Small window through fullscreen, each GUI scale, JEI open, search down to zero matches | All controls remain reachable, rows stay within bounds, hotbar stays fixed and the ring and held total agree with server counts. |
| Production build in Auram with many distinct NBT-heavy stacks and multiple active players | Capture timings and network traffic during idle, search, transfer and pickup. Investigate per-tick full scans, repeated full-store packets and conflicting mixins against the baseline. |

The initial dev client uses JEI 15.20.0.129, Jade 11.13.2 and Curios 5.14.1+1.20.1. Later integration candidates found in the supplied instance include Stack Refill 4.9, Mouse Tweaks 2.25.1, Inventory Essentials 8.2.14, Inventory Mending 1.2, Inventory Totem 3.4 and Keep Inventory Always 1.0.3. Loading these mods and validating their inventory behavior are separate milestones.

Forge references for implementation and test design include the [1.20.1 development setup](https://docs.minecraftforge.net/en/1.20.1/gettingstarted/), [capability lifecycle and synchronization](https://docs.minecraftforge.net/en/1.20.1/datastorage/capabilities/) and [1.20.1 Forge builds](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html).
