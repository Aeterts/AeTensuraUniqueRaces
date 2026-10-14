<p align="center">
  <img src="art/icon_512.png" alt="Tensura: Unique Races (Fabric)" width="160">
</p>

# Tensura: Unique Races (Fabric)

An add-on for [Tensura: Reincarnated](https://modrinth.com/mod/tensura-reincarnated) (Fabric, Minecraft 1.21.1) that
limits how many players on a server can have the same race at once. By default a race belongs to **one player only**:
once somebody has taken it, everybody else has to pick something else.

It is built for multiplayer from the start. Everything is decided on the server, claims are stored per player UUID and
survive restarts, offline players keep their race, and clients do not need the mod.

> Not affiliated with the Tensura: Reincarnated team.

**Documentation:** [guide](docs/GUIDE.md) · [configuration reference with recipes](docs/CONFIG.md) · [changelog](CHANGELOG.md)

## Features

- **One player per race**, or any number you like: a global limit, per-race limits and "strictly unique" races.
- **Evolution trees are reserved.** Taking Elf also holds Enlightened Elf, Elf Saint and Divine Elf, so nobody can take
  the form you will need to evolve into.
- **Human is free.** The human line is exempt by default; everything about it is configurable.
- **Taken races are refused before anything is given out.** In the reincarnation menu the player gets a chat message and
  the menu opens again, without skills or EP being handed out for a choice that did not happen.
- **Server-side only.** No client part, no network packets, no resource packs.
- **Persistent.** Claims live in the world data and survive restarts; offline players keep their race.
- **Administrator tools.** `/tensura unique show|release|reload`, players that are never limited, automatic release of
  inactive players, refusals written to the log.
- **Fully configurable**, including the texts of every message. Changes apply with `/tensura unique reload`.
- **Works with other Tensura add-ons.** Races added by add-ons are picked up from the registry without any setup.

## Requirements

| | |
|---|---|
| Minecraft | 1.21.1 |
| Fabric Loader | 0.16.10+ |
| Fabric API | any build for 1.21.1 |
| Tensura: Reincarnated | 2.0.1.3+ (it brings ManasCore, Architectury, GeckoLib, SmartBrainLib and TerraBlender) |
| Java | 21 |

## Installation

1. Put `tensura-unique-races-fabric-<version>.jar` into the `mods` folder of the **server**.
2. Start the server once. `config/tensura_unique_races.json` is created with every setting and its default.
3. That is all: clients do not install anything.

In single player the mod should work the same way (the integrated server does the same job); that mode has not been
tested separately.

If you add the mod to a world that already has players, their current races are recorded as taken the next time they
join. Nobody is demoted if several players already share a race; they are only recorded and a warning is logged.

## How it works

- A player **claims** the race they get (reincarnation menu, reset scroll, ...). While the claim exists, other players
  cannot take that race once the limit is reached.
- A claim reserves the **evolution tree** of its race (awakening and Harvest Festival forms included). The tree does not
  run into other base races (Goblin leads to Ogre and Wight to Human, but those are picked on their own). A form shared by
  two trees goes to whoever evolves into it first.
- **Evolving keeps the claim.** Choosing another race, or a reset (which makes you Human), releases it. Dying does not.
- A refused choice shows a chat message and opens the menu again; a refused evolution simply does not happen.
- Awakening, Harvest Festival, curses (for example into a Wight) and administrator commands (`/tensura race ...`) are
  **never blocked**: Tensura applies their side effects even when the race change fails, so cancelling them would leave
  the player in a broken state. They are only recorded.
- On login the claim is compared with the race the player really has, so changes made by operators are picked up.

The full rules, including a table of the default trees, are in the [guide](docs/GUIDE.md#2-how-it-works).

### Known behaviour

- A player who returns after an administrator released their claim claims the race again on login, even over the limit if
  somebody took it in the meantime (this is logged). Use `inactiveReleaseDays` to free the races of players who left.
- Worlds that already contain duplicate races keep them; the claims are just recorded.

## Commands

All commands extend the existing `/tensura` root command.

| Command | Who | What it does |
|---|---|---|
| `/tensura unique show` | everybody (`showPermissionLevel`) | lists the taken races and who holds them |
| `/tensura unique release race <race>` | operators (`adminPermissionLevel`) | frees a race; players keep their race, only the claim is removed |
| `/tensura unique release player <name>` | operators | frees the claim of a player, also an offline one |
| `/tensura unique reload` | operators | reloads the config without a restart |

## Configuration

`config/tensura_unique_races.json` is created on the first start with every setting; a missing setting uses its default.
Race ids are written like `tensura:goblin`. The most useful settings:

| Setting | Default | Description |
|---|---|---|
| `maxPlayersPerRace` | `1` | how many players may hold one race, `0` = unlimited |
| `perRaceLimits` | `{}` | limits of single races, e.g. `{"tensura:slime": 2}` |
| `strictlyUniqueRaces` | `[]` | races that are always limited to one player |
| `exemptRaces` | the human line | races anyone can take, they create no claims |
| `lockEvolutionPaths` | `true` | a claim reserves the evolution tree; `false` = only the current race is held |
| `bypassPlayers`, `bypassPermissionLevel` | none | players who are never limited |
| `inactiveReleaseDays` | `0` | release the claims of players away for this many days, `0` = never |
| `checkReincarnationMenu`, `checkEvolutions` | `true` | what is refused |
| `showHolderNames` | `true` | show who holds a race in `show` and in refusals |
| `messages.*` | see file | every text, with `&` colour codes and `{race}` `{holders}` `{limit}` placeholders |

Every setting, the exact rules for deciding whether a race is taken and sixteen ready-made recipes are in the
[configuration reference](docs/CONFIG.md).

## Project layout

```
src/main/java/org/aeterts/tensuraunique/
  TensuraUniqueRaces.java     entry point: wires the race event, join/leave/tick events and the commands
  Claims.java                 the rules: check, claim, release, reopening of the menu, inactive claims
  ClaimState.java             the claims as saved world data (per player UUID)
  RaceGraph.java              evolution trees, read from the ManasCore race registry
  UniqueConfig.java           the config model, loading and message formatting
  UniqueCommand.java          /tensura unique ...
  mixin/ReincarnationMenuMixin.java   refuses taken races in Tensura's reincarnation menu
  DevTest.java                development-only self-test (not part of the released jar)
art/                          the Blockbench model and renders of the icon
docs/                         guide and configuration reference
```

How it hooks into Tensura:

- `RaceEvents.SET_RACE` (ManasCore) is fired by every race change. The mod uses it to refuse ordinary evolutions and to
  record every change.
- Tensura's reincarnation menu ignores the result of the race change and hands out skills and EP anyway, so a taken race
  has to be refused **before** that. This is what `ReincarnationMenuMixin` does, and then it opens the menu again.

## Building

Requires **JDK 25 or newer** to build (Fabric Loom 1.18 needs it to run Gradle); the mod itself is compiled for and runs on
Java 21. IntelliJ IDEA can import the project directly: it reads the Gradle version from
`gradle/wrapper/gradle-wrapper.properties` and creates the Fabric Loom run configurations. Without an IDE use Gradle 9.8:

```
gradle build      # the jar is build/libs/tensura-unique-races-fabric-<version>.jar
```

Notes:

- Tensura: Reincarnated and the mods it needs are all rights reserved. They are resolved from Modrinth's maven only to
  compile and to test, and are never bundled into the mod.
- ManasCore keeps its modules (race, network, ...) as jar-in-jar inside its main jar and publishes them nowhere else, so
  `build.gradle` unpacks them into `build/manascore` to compile against.
- The project uses the official Mojang mappings: Tensura does not remap onto Yarn (a method name clashes).
- The compiler may print a warning about `method_7604`: the mixin names both the development and the production name of
  `clickMenuButton` on purpose.

### Development and testing

Use the `runServer` and `runClient` Loom tasks (or the generated IDEA run configurations). On a dev server accept the EULA
in `run/eula.txt`.

In a development environment the command `/tensura unique devtest` plays fake players through the real reincarnation menu,
the evolution calls and the commands, and checks every rule and every config option. It reports the failures and a
summary (the full report is in the server log) and is not part of the released jar. Please run it before sending a pull
request.

### Status

Checked on a dedicated server with two players (refusal message, menu opens again, `show`) and by the self-test above.

## Contributing

Issues and pull requests are welcome. For a bug report please include the versions of Minecraft, Fabric Loader, Tensura,
ManasCore and this mod, the relevant part of the server log (turn on `logDenials` to see refusals) and your
`tensura_unique_races.json`.

## License

[MIT](LICENSE). Copyright (c) 2026 Aeterts.
