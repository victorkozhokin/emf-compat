# EMF Compat: Create — Changelog

## 2.1.0

- Requires EMF Compat Core 2.3.0
- Skyhook on Fabric no longer freezes your character: faces and your resource pack keep working while you hang
- Fixed Create's engineer and logistics hat under Fresh Animations: it sits on the mob's head again instead of sinking to the neck or floating beside animals. Setting: "Hats on mobs"
- Added support for Barehanded on 1.21.1
- Added a Forge 1.20.1 build with Skyhook, hats and Create Stuff 'N Additions

## 2.0.0

- Now requires Entity Model Features 3.3.2 and EMF Compat Core 2.0.0
- Updated for EMF 3.3: the jetpack jump-suppression hooks the animation variable in its new home, and the flight check reads the new render state

## 1.4.1 — Fabric (Create Fly)

- Fixed the addon not listing Create Fly as required, which crashed the game on startup when it was installed on its own

## 1.4.0 — NeoForge (Create)

- Added a config tab with a toggle for every supported add-on
- Added built-in support for Create Cosmonautics and Create Stuff 'N Additions — the separate addons can be removed
- Fixed Skyhook on Create 6.x — half the body no longer slides out of the hanging pose
- Grappling hooks reworked the same way, poses stay correct while you swing
- Your character keeps its expressions and idle motion while skyhooking or grappling

## 1.4.0 — Fabric (Create Fly)

- Added a config tab with a master toggle and the NotEnoughAnimations item-swap fix
- You keep the Skyhook hanging pose while riding chains and ropes, instead of half your body sliding back into the resource-pack animation

Known limitation: while skyhooking, the model falls back to the vanilla one, so expression and
idle animations from your pack pause for the duration. The NeoForge build already keeps them —
Fabric will follow.
