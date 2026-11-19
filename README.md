# BigInventory

Forge development project for Auram's weighted player inventory. This checkpoint registers BigInventory and its server config. The storage engine, screen, HUD and gameplay integrations are planned in [the implementation and QA plan](docs/PLAN.md).

## Run the development client

Use a Java 17 JDK and the included Gradle wrapper. The project targets Minecraft 1.20.1, Forge 47.4.10 and official 1.20.1 mappings.

```sh
./dev-mods/import.sh
./gradlew runClient
```

The importer copies Auram's JEI 15.20.0.129, Jade 11.13.2 and Curios 5.14.1+1.20.1 from the default macOS PrismLauncher instance. To use another location, pass its mods directory as the argument to the importer. On Windows, you can also place the three original jars directly in `dev-mods` and run `gradlew.bat runClient`. The exact artifact names and versions are in [the dev mod list](dev-mods/auram.txt).

ForgeGradle remaps the local copies for the development runtime. The third-party jars stay local. The client uses `run/` for its worlds, config and logs.

## Run the dedicated server

From the repository root, initialize the development server's EULA file and launch it. Type `stop` in its console to save and exit.

```sh
mkdir -p run-server
echo eula=true > run-server/eula.txt
./gradlew runServer
```

The default world generates `run-server/world/serverconfig/biginventory-server.toml`. Its `[storage]` section has `baseCapacity = 500` and `footprintFactor = 32`. The footprint factor accepts integers from 1 through 8192. Gameplay enforcement is scheduled for the storage milestone.

## Build

```sh
./gradlew build
```

The reobfuscated mod jar is written to `build/libs/`. For a checkout without the Auram jars, use `./gradlew -PauramTestMods=false build`. The same property works with `runClient` or `runServer` for a Forge-only baseline.
