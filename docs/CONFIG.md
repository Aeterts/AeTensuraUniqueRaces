# Configuration — `tensura_unique_races.json`

For the overall description of the mod see [GUIDE.md](GUIDE.md).

Contents

1. [Using the config](#1-using-the-config)
2. [The default file](#2-the-default-file)
3. [Reference](#3-reference)
4. [How the mod decides whether a race is taken](#4-how-the-mod-decides-whether-a-race-is-taken)
5. [Messages and colours](#5-messages-and-colours)
6. [Recipes](#recipes)
7. [Common mistakes](#7-common-mistakes)

---

## 1. Using the config

- **File:** `config/tensura_unique_races.json` on the server (in single player, in the game's `config` folder). It is created
  on the first start with every setting.
- **Applying changes:** save the file and run `/tensura unique reload` in the game or the console (operator rights are
  needed). The server does not have to be restarted.
- **Every setting is optional.** A missing setting uses its default. On every load (also on `reload`) the file is
  **rewritten** with all settings, so the missing ones appear by themselves.
- **JSON format.** Comments and trailing commas are not officially supported (even if the file happens to be read, the
  comments disappear when the file is rewritten); strings are in double quotes. Unknown keys are dropped when the file is
  rewritten.
- **A broken file.** If the JSON cannot be read, the mod writes an error to the server log, **uses the defaults for now and
  does not touch the file**. Fix the file and run `reload`.
- **Race ids** are written as `namespace:name`: `tensura:goblin`, `tensura:elf`, `tensura:slime`. An invalid id produces a
  warning in the log and the entry is ignored. Tensura races by group:

| Group | Races |
|---|---|
| Base races (reincarnation menu) | `human`, `elf`, `dwarf`, `merfolk`, `beastfolk`, `harpy`, `goblin`, `lizardman`, `ogre`, `orc`, `slime`, `wight`, `ghoul`, `lesser_daemon`, `giant` |
| The human line | `human`, `enlightened_human`, `human_saint`, `divine_human` |
| Vampires | `vampire`, `vampire_overcomer`, `vampire_lord`, `divine_vampire` |

For the other races, type `/tensura unique release race ` and press Tab: the completion lists every race in the
registry, including races from other add-ons.

---

## 2. The default file

```json
{
  "enabled": true,
  "logDenials": false,
  "maxPlayersPerRace": 1,
  "perRaceLimits": {},
  "strictlyUniqueRaces": [],
  "exemptRaces": [
    "tensura:human",
    "tensura:enlightened_human",
    "tensura:human_saint",
    "tensura:divine_human"
  ],
  "exemptIncludesEvolutions": false,
  "lockEvolutionPaths": true,
  "noTreeLockRaces": [],
  "treeStopsAtBaseRaces": true,
  "extraTreeRaces": {},
  "excludeFromTree": {},
  "bypassPlayers": [],
  "bypassPermissionLevel": -1,
  "inactiveReleaseDays": 0,
  "checkReincarnationMenu": true,
  "checkEvolutions": true,
  "reopenMenuAfterDenial": true,
  "reopenDelayTicks": 5,
  "showPermissionLevel": 0,
  "adminPermissionLevel": 2,
  "showHolderNames": true,
  "language": "auto",
  "messages": {
    "taken": "&cThe race &e{race}&c is already taken by &f{holders}&c. Choose another one.",
    "full": "&cThe race &e{race}&c is full (&f{holders}&c). Choose another one.",
    "someone": "another player",
    "noFreeRace": "&cEvery race of this pool is already taken.",
    "noneTaken": "&7No race is taken yet.",
    "showHeader": "&6Taken races ({count}):"
  }
}
```

---

## 3. Reference

### General

| Setting | Type | Default | Description |
|---|---|---|---|
| `enabled` | true/false | `true` | Master switch. With `false` the mod checks and records nothing (claims already saved in the world stay). |
| `logDenials` | true/false | `false` | Write a line to the server log every time a player is refused a race (who, which race, who holds it). Useful for debugging. |

### Limits

| Setting | Type | Default | Description |
|---|---|---|---|
| `maxPlayersPerRace` | number | `1` | How many players may have the same race at once. `0` or less means unlimited. |
| `perRaceLimits` | object | `{}` | Limits of single races, they override `"maxPlayersPerRace"`: `{"tensura:slime": 2, "tensura:orc": 0}` (slime is limited to 2, orc is unlimited). A value of `0` or less means unlimited. |
| `strictlyUniqueRaces` | list | `[]` | Races that only one player can hold, whatever the other limits say. Highest priority. |
| `exemptRaces` | list | the human line | Races anyone can take; they create no claims and have no limit. |
| `exemptIncludesEvolutions` | true/false | `false` | When `true`, not only the races in `exemptRaces` but **their whole evolution tree** is free. Be careful: Human evolves into Vampire, so with this setting vampires become free as well. |

Priority when the limit of a race is calculated: `strictlyUniqueRaces` (always 1) → `perRaceLimits` → `maxPlayersPerRace`.

### Evolution trees

| Setting | Type | Default | Description |
|---|---|---|---|
| `lockEvolutionPaths` | true/false | `true` | A claimed race reserves its whole evolution tree (nobody can take a form you need for your evolution). With `false` only the race the player has now is held. |
| `noTreeLockRaces` | list | `[]` | Races (by the root of the claim) whose tree is **not** reserved even when `lockEvolutionPaths` is `true`. |
| `treeStopsAtBaseRaces` | true/false | `true` | A tree does not run into other base races of the reincarnation pools (Goblin → Ogre, Wight → Human). With `false` another player's tree can swallow a base race and it can no longer be picked. |
| `extraTreeRaces` | object | `{}` | Extra races a tree reserves. For add-ons whose races Tensura does not link by itself: `{"tensura:slime": ["someaddon:rimuru_form"]}`. |
| `excludeFromTree` | object | `{}` | Races removed from a tree: `{"tensura:goblin": ["tensura:divine_oni"]}`. |

The keys of `extraTreeRaces` and `excludeFromTree` and the values of `noTreeLockRaces` are the **root of the claim**, that
is, the race the player started with.

### Who is affected

| Setting | Type | Default | Description |
|---|---|---|---|
| `bypassPlayers` | list | `[]` | Players the limits do not apply to: names (case-insensitive) or UUIDs (with dashes). They do not claim races and can take any. |
| `bypassPermissionLevel` | number | `-1` | Players with this permission level or higher are not limited. `-1` means nobody. Ordinary operators have level 4. |
| `inactiveReleaseDays` | number | `0` | Release the claims of players who have been offline for longer than this many days. `0` means never. Checked when the server starts and every half hour; players who are online are never affected. |

### What is checked

| Setting | Type | Default | Description |
|---|---|---|---|
| `checkReincarnationMenu` | true/false | `true` | Refuse taken races in the reincarnation menu (random choice included). |
| `checkEvolutions` | true/false | `true` | Refuse ordinary evolutions into a race another player holds. Awakening, the Harvest Festival, curses and administrator commands are never blocked. |
| `reopenMenuAfterDenial` | true/false | `true` | Open the reincarnation menu again after a refusal (otherwise the player is left without the menu until they rejoin or reset). |
| `reopenDelayTicks` | number | `5` | How many ticks to wait before the menu opens again (20 ticks = 1 second). Minimum 1. A value that is too small may not outlast the client closing the screen. |

### Commands

| Setting | Type | Default | Description |
|---|---|---|---|
| `showPermissionLevel` | number | `0` | Permission level for `/tensura unique show` (0 means everybody). |
| `adminPermissionLevel` | number | `2` | Permission level for `release` and `reload`. |
| `showHolderNames` | true/false | `true` | Show who holds a race in `show` and in the refusal message. With `false`, `show` prints a number and the message uses the text `messages.someone`. |

Permission levels in Minecraft: 0 — everybody, 1 — moderators, 2 — operators (commands), 3 — administrators, 4 — owner.
The level an operator gets is set in `server.properties` (`op-permission-level`, 4 by default).

### Language and messages

| Setting | Type | Default | Description |
|---|---|---|---|
| `language` | text | `auto` | Language of the built-in texts. `auto` uses the language of each player's game (English when it is not available, for example in the console), or write a fixed one such as `en_us`, `ru_ru` or `de_de` (`en`, `ru`, `de` work too; the full list is in section 5). A language without a file falls back to English. |
| `messages.*` | text | see the default file | The message templates, see [section 5](#5-messages-and-colours). |

---

## 4. How the mod decides whether a race is taken

Player P wants race R.

1. If the mod is off, the check is off (`checkReincarnationMenu` / `checkEvolutions` for that kind of change), the player
   is exempt (`bypassPlayers`, `bypassPermissionLevel`) or R is free (`exemptRaces`) — allowed.
2. If R is already P's current race — allowed.
3. Otherwise the **other players who hold R** are counted. Player Q holds R if:
   - R is Q's current race, **or**
   - Q's tree is reserved (`lockEvolutionPaths`, and the root of Q is not in `noTreeLockRaces`) and R is part of the tree
     rooted at Q's root.
4. If P already owns a tree that contains R (an evolution inside their own tree), only the players who are **standing** on R
   right now are counted (a node shared by two trees goes to whoever got there first).
5. The number of holders is compared with the limit of R (see "Limits"). If it is **not smaller** than the limit — refused.

A **tree** is built from the root: ordinary evolutions, awakening and the Harvest Festival, recursively. The walk does not
continue into other base races (when `treeStopsAtBaseRaces` is on); then `extraTreeRaces[root]` is added and
`excludeFromTree[root]` is removed; the root is always part of its tree.

The default trees (Tensura 2.0.1.3), so that you can see what gets reserved:

| Root | Tree |
|---|---|
| `elf` | elf, enlightened_elf, elf_saint, divine_elf |
| `dwarf` | dwarf, enlightened_dwarf, dwarf_saint, divine_dwarf |
| `goblin` | goblin, hobgoblin_saint, divine_oni (the other forms of the chain, which go through the base race `ogre`, are not included) |
| `ogre` | ogre, enlightened_ogre, kijin, spirit_oni, mystic_oni, wicked_oni, divine_oni, death_oni, divine_fighter |
| `slime` | slime, demon_slime, metal_slime, god_slime |
| `wight` | wight, wight_king, spirit_skeleton, divine_skeleton |
| `ghoul` | ghoul, vampire_lord, divine_vampire |

The real trees on your server depend on the versions of Tensura and of the add-ons; in a development environment
`/tensura unique devtest` prints them.

---

## 5. Messages and colours

The `messages.*` settings are text templates.

| Key | Shown when | Placeholders |
|---|---|---|
| `taken` | a refusal, when the limit of the race is 1 | `{race}`, `{holders}`, `{limit}` |
| `full` | a refusal, when the limit is greater than 1 | `{race}`, `{holders}`, `{limit}` |
| `someone` | used instead of the names when `showHolderNames` is `false` | — |
| `noFreeRace` | a random choice found no free race | — |
| `noneTaken` | `show`, when nothing is taken | — |
| `showHeader` | the heading of the `show` list | `{count}` — the number of taken races |

Placeholders: `{race}` is the race name (in the player's language, as in Tensura itself), `{holders}` the player names
separated by commas, `{limit}` the limit (`-` for an unlimited race).

**Colours and styles** with `&`: `&0`–`&9`, `&a`–`&f` (colours), `&l` (bold), `&r` (reset). Other styles (italic,
underline) are not supported.

| Code | Colour | Code | Colour |
|---|---|---|---|
| `&0` | black | `&8` | dark gray |
| `&1` | dark blue | `&9` | blue |
| `&2` | dark green | `&a` | green |
| `&3` | dark aqua | `&b` | aqua |
| `&4` | dark red | `&c` | red |
| `&5` | dark purple | `&d` | light purple |
| `&6` | gold | `&e` | yellow |
| `&7` | gray | `&f` | white |

**Languages.** The built-in texts exist in 33 languages and are chosen per player (see `language`): `en_us, ru_ru, uk_ua, de_de, fr_fr, es_es, pt_br, it_it, pl_pl, tr_tr, zh_cn, zh_tw, ja_jp, ko_kr, nl_nl, cs_cz, sk_sk, sv_se, da_dk, fi_fi, no_no, hu_hu, ro_ro, el_gr, bg_bg, hr_hr, lt_lt, lv_lv, et_ee, th_th, vi_vn, id_id, ca_es`. A regional variant uses the file of its language (`de_at` gets `de_de`, `es_mx` gets `es_es`, `pt_pt` gets `pt_br`, `zh_hk` gets `zh_tw`). A message you
leave at its default (or empty) is replaced by the built-in text of the player's language, so a config written by an older
version that still contains the English defaults is localized as well. A message you change is used exactly as written, for
every player and every language: the mod is server-side only, so it cannot translate your text on the client.

---

## Recipes

Each example shows only the settings that change; the others can be left out, missing settings are filled in
automatically.

**1. Two players per race**
```json
{ "maxPlayersPerRace": 2 }
```

**2. One player per race, two for slime**
```json
{ "perRaceLimits": { "tensura:slime": 2 } }
```

**3. Free for all, except a few rare races**
```json
{
  "maxPlayersPerRace": 0,
  "strictlyUniqueRaces": ["tensura:lizardman", "tensura:lesser_daemon", "tensura:giant"]
}
```

**4. Only the current race is held, no tree reservation**
```json
{ "lockEvolutionPaths": false }
```

**5. Reserve the trees of all races except slime**
```json
{ "noTreeLockRaces": ["tensura:slime"] }
```

**6. Administrators and organizers without limits**
```json
{
  "bypassPermissionLevel": 4,
  "bypassPlayers": ["Steve", "069a79f4-44e9-4726-a5be-fca90e38aaf5"]
}
```

**7. Release the races of players who have been away for a month**
```json
{ "inactiveReleaseDays": 30 }
```

**8. Hide who holds which race**
```json
{ "showHolderNames": false }
```

**9. Let only operators see the list of taken races**
```json
{ "showPermissionLevel": 2 }
```

**10. Your own wording**
```json
{
  "messages": {
    "taken": "&6Sorry, &e{race}&6 already belongs to &f{holders}&6. Pick another race!",
    "full": "&6Sorry, &e{race}&6 is full (&f{holders}&6). Pick another race!",
    "someone": "someone else",
    "noFreeRace": "&6All races of this list are taken.",
    "noneTaken": "&7No race is taken yet.",
    "showHeader": "&6Taken races ({count}):"
  }
}
```

**11. Check the reincarnation menu only (evolutions are not limited)**
```json
{ "checkEvolutions": false }
```

**12. Check evolutions only**
```json
{ "checkReincarnationMenu": false }
```

**13. Make the vampire line free as well**
```json
{
  "exemptRaces": [
    "tensura:human", "tensura:enlightened_human", "tensura:human_saint", "tensura:divine_human",
    "tensura:vampire", "tensura:vampire_overcomer", "tensura:vampire_lord", "tensura:divine_vampire"
  ]
}
```

**14. Link the races of an add-on to the slime tree**
```json
{
  "extraTreeRaces": { "tensura:slime": ["someaddon:rimuru_form", "someaddon:rimuru_final"] }
}
```

**15. Turn the mod off for a while**
```json
{ "enabled": false }
```

**16. Find out why a player was refused**
```json
{ "logDenials": true }
```
Then look at the server log: `PlayerName was refused tensura:elf (held by [Steve])`.

**17. One language for everybody**
```json
{ "language": "ru_ru" }
```

---

## 7. Common mistakes

| Mistake | What happens |
|---|---|
| An unclosed bracket, a missing comma between elements | The file cannot be read. The mod logs the error and temporarily uses the defaults without overwriting your file. |
| `//` comments and a comma after the last element | Not supported: even if the file is read, the comments disappear on the next rewrite. Keep notes elsewhere. |
| `"tensura:Goblin"`, `"goblin"` | Race ids are lowercase with the `tensura:` namespace. An invalid entry is ignored (a warning is logged). |
| `bypassPermissionLevel: 0` | This means "every player is exempt", so the mod is effectively off. Use `-1` for "nobody". |
| `maxPlayersPerRace: 0` | This is "unlimited", not "nobody". |
| Edited the config but forgot `reload` | The new values apply after a restart or after `/tensura unique reload`. |
| Lowered a limit and expect surplus players to be kicked out | Existing claims are not cancelled; the limit applies to the next choices. |
| Added a race to `exemptRaces` but the player is still in `show` | The record is updated the next time the player changes race or joins. To release it at once use `/tensura unique release race <race>`. |
