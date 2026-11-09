# EMF Compat: Better Combat — Changelog

## 2.1.2

### Added

- Tells Touch'n Motion when an attack or a weapon stance holds the arms (needs EMF Compat Core 2.4.0; nothing changes with an older core)

### Fixed

- The hand keeps time with the weapon: an attack's arms go straight to Better Combat's pose instead of easing in a frame or two late (the end still eases out); **Smooth attack start** brings the old ease-in back, off by default
- Fabric 1.21.11 and newer: weapon stance rendering no longer risks a helper-method conflict with another compat addon

### Removed

- The mace plays Better Combat's own slam: the softened copy this addon swapped in is gone, on every version (on Fabric 1.21.1 it also made the mace's attack disappear, GitHub #49)

## 2.1.1

### Added

- Forge 1.20.1: Spell Engine and the RPG Series are covered there too - casting poses stay visible, with the **RPG Series & other animations** option in the settings

### Changed

- Weapon stances are on by default: two-handed weapons keep their stance without a visit to the settings (Forge 1.20.1, NeoForge 1.21.1, Fabric 1.21.1; newer versions always had it)

## 2.1.0

### Added

- Added a Fabric 26.3 build
- Added a Fabric 1.21.1 build

### Dependencies

- Requires EMF Compat Core 2.3.0

## 2.0.0

### Fixed

- Fixed the body and legs freezing during an attack: EMF 3.3 moved the decision that pauses its animations, and the addon was lifting the pause in a place that is no longer consulted

### Dependencies

- Now requires Entity Model Features 3.3.2 and EMF Compat Core 2.0.0

## 1.1.1

### Added

- Added support for Entity Model Features 3.3 (needs EMF Compat Core 1.2.0)

## 1.1.0

### Added

- Added a config tab with toggles for arm sync, attack legs, weapon stances and the RPG Series support
- Added precise support for weapon stances: spear, trident, claymore and the rest keep their stance (off by default)
- Added attack footwork: your legs show the step while standing still, and give way to the walk cycle once you move
- Added support for RPG Series and Spell Engine — casting poses stay visible (Archers, Wizards, Paladins & Priests, Rogues & Warriors etc)

### Changed

- Softer torso lean during attacks, so swings don't tip your whole body over

### Fixed

- Fixed the model flicking back to vanilla for a few frames right after an attack
- Other animation mods keep working normally — the addon no longer switches EMF's animation pause off for everything
