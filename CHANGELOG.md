# Changelog

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
