# Changelog

## 1.2.0

* Texts in 33 languages: English, Russian, Ukrainian, German, French, Spanish, Brazilian Portuguese, Italian, Polish, Turkish,
  Simplified and Traditional Chinese, Japanese, Korean, Dutch, Czech, Slovak, Swedish, Danish, Finnish, Norwegian, Hungarian,
  Romanian, Greek, Bulgarian, Croatian, Lithuanian, Latvian, Estonian, Thai, Vietnamese, Indonesian and Catalan. Other than
  English and Russian, the translations are machine-assisted and corrections are welcome.
* A regional variant uses the file of its language (de_at gets de_de, es_mx gets es_es, pt_pt gets pt_br, zh_hk gets zh_tw).
* A new language is a file in `assets/tensura_unique_races_fabric/lang/` plus its code in `languages.json`.

## 1.1.0

* Russian translation. The mod comes with English and Russian texts and the server picks the language of each player's game
  (the new `language` setting can force one language). Command feedback is translated too.
* A message changed in the config is still used as written; messages left at their defaults are localized, also in configs
  created by 1.0.0.
* A new language is a single file: `assets/tensura_unique_races_fabric/lang/<code>.json`.

## 1.0.0

First release. Fabric, Minecraft 1.21.1, Tensura: Reincarnated 2.0.1.3+.

* One player per race (the limit is configurable); claims are server-side, stored per player UUID, survive restarts and
  keep working for offline players.
* A claim reserves the evolution tree of the race (stops at other base races); evolving keeps the claim, choosing
  another race or resetting releases it.
* A taken race is refused in the reincarnation menu (the menu opens again) and for ordinary evolutions. Awakening,
  harvest festival, curses and admin commands are recorded but never blocked.
* Commands: `/tensura unique show`, `release race|player`, `reload`.
* Config `config/tensura_unique_races.json`: limits (global, per race, strictly unique), exempt races, evolution trees,
  bypass players / permission level, release of inactive players, what is checked, command permissions, messages.
* Server-side only: clients do not need the mod.
