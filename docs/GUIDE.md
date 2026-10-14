# Ae's Tensura: Unique Races — guide

An add-on for [Tensura: Reincarnated](https://modrinth.com/mod/tensura-reincarnated) on Fabric 1.21.1. It limits how many
players on a server can have the same race at the same time. By default a race can be taken by one player only.
Every setting is described in [CONFIG.md](CONFIG.md).

Contents

1. [Installation and compatibility](#1-installation-and-compatibility)
2. [How it works](#2-how-it-works)
3. [What the player sees](#3-what-the-player-sees)
4. [What the mod never blocks](#4-what-the-mod-never-blocks)
5. [Commands](#5-commands)
6. [Where the data is stored](#6-where-the-data-is-stored)
7. [Common situations](#7-common-situations)
8. [Troubleshooting](#8-troubleshooting)
9. [Building and development](#9-building-and-development)

---

## 1. Installation and compatibility

| Component | Version |
|---|---|
| Minecraft | 1.21.1 |
| Fabric Loader | 0.16.10 or newer |
| Fabric API | any build for 1.21.1 |
| Tensura: Reincarnated | 2.0.1.3 or newer (it needs ManasCore, Architectury, GeckoLib, SmartBrainLib and TerraBlender) |

**Installing.** Put `ae-tensura-unique-races-fabric-<version>.jar` into the `mods` folder of the **server**. Clients do not
need the mod: it has no client code and no network packets of its own, and every message to a player is an ordinary
chat message. In single player the mod should work the same way (the integrated server does the same job), but that mode
has not been tested separately.

**First start.** The mod creates `config/tensura_unique_races.json` with every setting and its default. If the mod is
added to an existing world, the races players already have are recorded as taken the next time they join (see
[section 7](#7-common-situations)).

**Other Tensura add-ons.** Races from other add-ons are picked up automatically: evolution trees are read from the
ManasCore race registry at runtime. If an add-on links its races in an unusual way, use `extraTreeRaces` and
`excludeFromTree` (see [CONFIG.md](CONFIG.md)).

---

## 2. How it works

### Claiming a race

When a player gets a race (choosing it in the reincarnation menu, after a reset scroll, and so on), the mod records a
**claim**: which race the player holds and which race the claim started with. While the claim exists, other players cannot
take that race once the limit (1 by default) is reached.

What is stored about each player:
- the **root** — the race the claim started with;
- the **current race** — what the player has now;
- the last known name and the time of the last login (for administrator commands and automatic release).

### Evolution trees

By default a claimed race reserves **its whole evolution tree**, so nobody can take a form you will need to evolve into.
For example, if you are an Elf:

```
elf → enlightened_elf → elf_saint → divine_elf
```

all four forms are closed to other players. The tree covers ordinary evolutions, awakening and the Harvest Festival.

Rules of the tree:
- **A tree does not run into other base races.** Base races are the ones that can be picked in the reincarnation menu
  (every Tensura pool). Goblin leads to Ogre and Wight leads to Human, but Ogre and Human are picked on their own and
  another player's tree does not swallow them (`treeStopsAtBaseRaces`).
- **Shared nodes.** If two trees have a form further down in common, it goes to whoever evolves into it first: a form
  that another player is already standing on cannot be taken.
- The tree can be adjusted by hand: add races (`extraTreeRaces`), remove races (`excludeFromTree`), turn the reservation
  off for single races (`noTreeLockRaces`) or altogether (`lockEvolutionPaths: false`, then only the race a player has
  right now is held).

### Free races

Human and its line (`tensura:human`, `enlightened_human`, `human_saint`, `divine_human`) are free for everybody: such races
create no claims and have no limit. Vampire (which Human evolves into) stays limited, which is why the human line is listed
explicitly instead of "with all its evolutions".

### What releases a claim

- **Choosing another race** in the reincarnation menu (a new race means a new claim, the old one is released).
- **A reset** with a scroll: it makes you Human, Human is free, so the claim is dropped until the next choice.
- **An administrator command**, `/tensura unique release ...`.
- **Automatic release** (`inactiveReleaseDays`) when a player has not been online for a long time.

What does **not** release a claim: dying and respawning (Tensura keeps the race on death), leaving the server, evolving.

### Atomicity

Checking and claiming are done on the server in one step: two players who confirm the same race in the same tick will
not both get it. The claim is compared with the player's real race at the end of the tick and on login, so if an
administrator changes a race with a Tensura command, the record fixes itself.

---

## 3. What the player sees

**Choosing in the reincarnation menu.** If the chosen race is taken, the player gets a chat message and the menu opens
again (with the same settings) a few ticks later: the Tensura client closes the screen when you confirm, so the mod opens
it again. No skills or EP are handed out on a refusal. This is different from "just cancelling the result": the Tensura
menu does not check whether the race change succeeded, so the mod refuses the choice **before** the skills are given.

**Random race.** If the player picks "random", the random race is chosen among the free ones only. If none is left, the
choice is refused with the `messages.noFreeRace` text.

**Evolution.** An ordinary evolution into a race another player holds (when the limit is reached) simply does not happen,
and the player sees the same message as for a refused choice.

The mod comes with texts in **33 languages** (English, Russian, Ukrainian, German, French, Spanish, Brazilian Portuguese, Italian, Polish, Turkish, Simplified and Traditional Chinese, Japanese, Korean, Dutch, Czech, Slovak, Swedish, Danish, Finnish, Norwegian, Hungarian, Romanian, Greek, Bulgarian, Croatian, Lithuanian, Latvian, Estonian, Thai, Vietnamese, Indonesian and Catalan). Because it is server-side only, the server picks the
language: a player gets the language of their game, and a language the mod does not have (or a console) gets English. A
regional variant uses the file of its language (Austrian German gets German, Mexican Spanish gets Spanish). The `language`
setting can force one language for everybody. The texts can also be changed (`messages.*`); a text you changed is sent as you
wrote it, in every language.

Apart from English and Russian, the translations were produced with machine assistance and have not been reviewed by native
speakers; corrections are welcome (see [Translations](../README.md#translations) in the README).

The default message looks like this:
- English: `The race Elf is already taken by Steve. Choose another one.`
- German: the same message, translated (`lang/de_de.json`), and so on.

---

## 4. What the mod never blocks

These changes are only **recorded** (the mod updates the claims) and are never cancelled. The reason is that Tensura applies
their side effects (rewards, messages, skills) whatever the result of the race change, so cancelling one would leave the
player in a broken state.

- Awakening and the Harvest Festival.
- Turning into another race by a curse (for example into a Wight).
- Administrator commands (`/tensura race ...`).
- Rimuru mode (a Tensura game rule): every player becomes a slime, so many slime claims are recorded.
- Any other race change made by other add-ons that is not an ordinary evolution or a choice in the menu.

If such a change leaves more players on a race than the limit allows, a warning is written to the server log when the
player joins, and `show` lists it as, for example, `(2/1)`. Nobody is demoted.

---

## 5. Commands

The commands are added to the existing `/tensura` root command.

| Command | Who can use it | What it does |
|---|---|---|
| `/tensura unique show` | everybody (`showPermissionLevel`, 0 by default) | Lists the taken races and who holds them. Format: ` - Race: Player1, Player2 (2/2)`. With `showHolderNames: false` only the number of players is shown. |
| `/tensura unique release race <race>` | operators (`adminPermissionLevel`, 2 by default) | Removes every claim whose current or root race is this one. Players keep their race, only the record is removed. |
| `/tensura unique release player <name>` | operators | Removes the claim of a player by name, also if the player is offline. The name is the one from the last login. |
| `/tensura unique reload` | operators | Re-reads `config/tensura_unique_races.json` without a restart. |

Tab completion works: races are suggested from the registry, player names from the recorded claims.

A released record does **not** change the player's race. If the player joins afterwards and the race is still free, the
claim is restored (see [section 7](#7-common-situations)).

---

## 6. Where the data is stored

| What | Where |
|---|---|
| Settings | `config/tensura_unique_races.json` (on the server) |
| Claims | `<world>/data/tensura_unique_races.dat` (saved world data, stored with the world) |

Claims are tied to the player's **UUID** and live in the overworld's data, so they survive a server restart, and offline
players keep their race (which stays taken). A backup of the world includes the claims. To reset all claims, stop the
server and delete `tensura_unique_races.dat` (players' races do not change, the claims are rebuilt when they join).

---

## 7. Common situations

**The mod is added to a server that already has players with races.** Every player gets a claim on their next login. If
several players already had the same race, all of them get a record (the limit does not kick them out) and a warning is
logged. New players can no longer take that race.

**A player wants to change their race.** Through a reset scroll (it makes them Human and opens the reincarnation menu
again). The old claim is dropped and a new one appears after the choice.

**An administrator released the race of a player who has not been online for a month.** Use `release player <name>`; the
race becomes free for others. If that player comes back and the race is still free, they claim it again; if somebody else
took it meanwhile, the login goes through and the race has to be sorted out by hand (there is a warning in the log). To
free such races automatically, turn on `inactiveReleaseDays`.

**The organizers need to be exempt.** List them in `bypassPlayers` (names or UUIDs) or set `bypassPermissionLevel` (for
example 4): such players are never limited and never claim a race.

**You want two players per race.** `maxPlayersPerRace: 2`.

More ready-made recipes: [CONFIG.md](CONFIG.md#recipes).

---

## 8. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| The server does not start: `requires version ... of tensura / manascore_race` | Tensura 2.0.1.3+ or ManasCore 4.0.0.2+ is missing. Update the mods. |
| A mixin error about `ReincarnationMenu` in the log | The Tensura version differs from the one the mod was built for (the reincarnation menu changed). Check the Tensura version and update this mod if needed. |
| A player chose a taken race and got it | Check `enabled` and `checkReincarnationMenu`; the player may be in `bypassPlayers` or have the rights from `bypassPermissionLevel`; the race may be in `exemptRaces`. |
| A player got no refusal message | Messages go to the chat; the player may have the chat disabled. Turn on `logDenials: true` and the refusals are written to the server log. |
| `show` is empty although players are playing | Claims are created when a race changes and when a player joins. If the mod was added to a running server, players must rejoin. Players on "free" races (Human) create no records. |
| Nothing changed after editing the config | Run `/tensura unique reload`. If the file has a JSON error, the mod logs it, uses the defaults and **does not touch the file**: fix it and reload. |
| A warning `invalid race id` | There is a typo in a race id in the config. The format is `namespace:name`, for example `tensura:goblin`. Invalid entries are ignored. |
| I changed `maxPlayersPerRace` and nobody was kicked out | That is by design: new limits apply to the next choices, existing claims are not cancelled. |
| After a refusal the menu did not open again | The menu opens after `reopenDelayTicks` ticks; if the player left in the meantime, Tensura opens it itself on the next login while the reset is unfinished. If it never opens, check `reopenMenuAfterDenial`. |

---

## 9. Building and development

See [README.md](../README.md): the build (`gradle build`, or import the project into IntelliJ IDEA), the dev server and the
self-test command `/tensura unique devtest` (development environment only, not part of the released jar).

License: MIT.
