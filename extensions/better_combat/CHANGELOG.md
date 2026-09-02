# EMF Compat: Better Combat — Changelog

## 2.0.0

- Now requires Entity Model Features 3.3.2 and EMF Compat Core 2.0.0
- Fixed the body and legs freezing during an attack: EMF 3.3 moved the decision that pauses its animations, and the addon was lifting the pause in a place that is no longer consulted

## 1.1.1

- Added support for Entity Model Features 3.3 (needs EMF Compat Core 1.2.0)

## 1.1.0

- Added a config tab with toggles for arm sync, attack legs, weapon stances and the RPG Series support
- Added precise support for weapon stances: spear, trident, claymore and the rest keep their stance (off by default)
- Added attack footwork: your legs show the step while standing still, and give way to the walk cycle once you move
- Added support for RPG Series and Spell Engine — casting poses stay visible (Archers, Wizards, Paladins & Priests, Rogues & Warriors etc)
- Softer torso lean during attacks, so swings don't tip your whole body over
- Fixed the model flicking back to vanilla for a few frames right after an attack
- Other animation mods keep working normally — the addon no longer switches EMF's animation pause off for everything
