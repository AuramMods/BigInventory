# BigInventory

BigInventory replaces the player backpack with weight-limited storage on Minecraft Forge 1.20.1. The nine hotbar slots, armor, offhand, and crafting grid have their own space.

## Play

With Patchouli installed, craft the **Big Inventory Book** from a book, gray dye, and a chest. Right-click it to open the guide to storage controls, weight, accessories, and server settings. The book is also in the creative Tools & Utilities tab. Its gray interface is adapted from the Extra Utilities Rebirth field guide.

Each backpack slot holds one stack. Use normal clicks to pick up or place stacks, right-click to split stacks or place one item, and drag to distribute items. Press a number key over a slot to swap it with the corresponding hotbar slot. Scroll to reach more slots, or search by item name, registry ID, or mod using a query such as `@Biomes Black`.

In your own inventory, shift-click to move stacks between the hotbar and backpack. In a chest or supported terminal, shift-click a backpack stack to send it to the open container. **Sort** compacts the whole backpack and groups stacks by item type. The player inventory and vanilla chests can show up to five backpack rows, depending on GUI scale.

The creative inventory tab and supported menus with custom player slots show grouped backpack entries.

The white ring beside the hotbar fills as you use storage. Crouch to see the percentage used. The count on the other side follows your selected non-tool item across storage and the hotbar. Using up a stack or breaking a tool can refill its empty hotbar slot from storage.

Server-side capacity defaults to 500. With the default footprint factor of 32, a cobblestone takes one unit, an empty bucket takes two, and an unstackable tool takes 32. If equipment changes put you over capacity, you keep your items and can withdraw them. Deposits resume when there is room. Movement slows by half and Mining Fatigue I applies while overloaded.

### Curios accessories

| Item | Slot | Effect |
| --- | --- | --- |
| Pouch | Hip | +98 capacity. The recipe takes its color from the wool, and any pouch can be recolored with dye. |
| Bag | Hip | +352 capacity and +15% movement exhaustion per equipped bag. Applies to sprinting, jumping, and swimming. Three bags give +45%. |
| Echo Ring | Ring | +50% of capacity after flat bonuses. Two rings give +100%. Craft with an echo shard above three gold ingots in a U shape. |
| Endant | Necklace | Items beyond the first stack of each stored variant weigh half as much. Only one Endant can be equipped. |

Curios gives players three Hip slots, two Ring slots, and one Necklace slot by default. Natural regeneration uses normal exhaustion. With an Endant, 128 stored cobblestone weigh 96. Fractional weight from all stored items is added together before the total is rounded down.

Village, dungeon, and other structure chests can contain a random-color Pouch or a rarer Bag. Bags are more common in Nether and End chests. End City treasure can contain an Endant, and Wardens have a 25% chance to drop an Echo Ring.

## Run the development client

Use a Java 17 JDK and the included Gradle wrapper. The project targets Minecraft 1.20.1, Forge 47.4.10 and official 1.20.1 mappings.

```sh
./dev-mods/import.sh
./gradlew runClient
```

The importer copies the dev mods from the default macOS PrismLauncher instance for Auram. The [shared dev mods](dev-mods/auram.txt) are JEI, Jade, Curios, and Patchouli. The [client-only test mods](dev-mods/client.txt) are Inventory Particles, its dependency MossyLib, and Smooth Swapping. To use another location, pass its mods directory as the argument to the importer. On Windows, you can place the original jars directly in `dev-mods` and run `gradlew.bat runClient`.

ForgeGradle remaps the local copies for the development runtime. `runClient` adds a reference map to a generated copy of Smooth Swapping so its mixins work with development mappings. The third-party jars stay local. The client uses `run/` for its worlds, config and logs.

## Run the dedicated server

From the repository root, initialize the development server's EULA file and launch it. Type `stop` in its console to save and exit.

```sh
mkdir -p run-server
echo eula=true > run-server/eula.txt
./gradlew runServer
```

The default world generates `run-server/world/serverconfig/biginventory-server.toml`. Its `[storage]` section contains `baseCapacity`, `footprintFactor`, `refillHotbar`, and `curioCapacity`. The footprint factor accepts integers from 1 through 8192. You can disable BigInventory's refill when testing another refill mod.

With Curios installed, add one rule per item such as `"pack:rucksack=150"` or `"pack:cursed_ring@ring=-50"` to `curioCapacity`. A slot-specific amount takes precedence over the general rule for that item. Configured changes are added to the built-in flat bonuses before Echo Rings are applied. Curios data controls slot counts and which items can be equipped.

## Build

Run all BigInventory builds and release checks locally using the development machine's Gradle cache. Do not add GitHub Actions workflows for this mod. Attach the locally built, reobfuscated jar to the GitHub release.

```sh
./gradlew build
```

The reobfuscated mod jar is written to `build/libs/`. For a checkout without the Auram jars, use `./gradlew -PauramTestMods=false build`. The same property works with `runClient` or `runServer` for a Forge-only baseline.

See [the implementation and QA plan](docs/PLAN.md) for pack integration coverage and later test cases.
