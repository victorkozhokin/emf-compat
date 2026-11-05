# EMF Compat: Create — Changelog

## 2.1.0

### Added

- Added support for Barehanded on 1.21.1
- Added a Forge 1.20.1 build with Skyhook, hats and Create Stuff 'N Additions

### Fixed

- Skyhook on Fabric no longer freezes your character: faces and your resource pack keep working while you hang
- Fixed Create's engineer and logistics hat under Fresh Animations: it sits on the mob's head again instead of sinking to the neck or floating beside animals. Setting: "Hats on mobs"

### Dependencies

- Requires EMF Compat Core 2.3.0

## 2.0.0

### Changed

- Updated for EMF 3.3: the jetpack jump-suppression hooks the animation variable in its new home, and the flight check reads the new render state

### Dependencies

- Now requires Entity Model Features 3.3.2 and EMF Compat Core 2.0.0

## 1.4.1 — Fabric (Create Fly)

### Fixed

- Fixed the addon not listing Create Fly as required, which crashed the game on startup when it was installed on its own

## 1.4.0 — NeoForge (Create)

### Added

- Added a config tab with a toggle for every supported add-on
- Added built-in support for Create Cosmonautics and Create Stuff 'N Additions — the separate addons can be removed

### Changed

- Your character keeps its expressions and idle motion while skyhooking or grappling

### Fixed

- Fixed Skyhook on Create 6.x — half the body no longer slides out of the hanging pose
- Grappling hooks reworked the same way, poses stay correct while you swing

## 1.4.0 — Fabric (Create Fly)

### Added

- Added a config tab with a master toggle and the NotEnoughAnimations item-swap fix

### Fixed

- You keep the Skyhook hanging pose while riding chains and ropes, instead of half your body sliding back into the resource-pack animation

### Notes

Known limitation: while skyhooking, the model falls back to the vanilla one, so expression and
idle animations from your pack pause for the duration. The NeoForge build already keeps them —
Fabric will follow.
