# EMF Compat: Better Combat

## [Modrinth](https://modrinth.com/project/emf-compat-better-combat)

A small client-side mod that makes **[Better Combat](https://modrinth.com/mod/better-combat)** attack animations work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

## Features

- Your weapon swings and charge poses look right instead of being overwritten by idle resource-pack animations.
- Body and head keep their EMF animations during attacks, so your character stays alive and breathing mid-swing.
- The attack's footwork shows on your legs while you stand still, and gives way to the normal walk cycle once you move.
- Held items align with your swing in first person, so weapons don't float next to your arm.
- Softer torso lean than stock Better Combat, so attacks don't tip your whole body over.
- Works with one-handed, two-handed and mace attacks.
- Compatible with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**.
- Should work with most player animation resource packs using EMF.

## RPG Series & spellcasting

With **[Spell Engine](https://modrinth.com/mod/spell-engine)** installed, casting poses are covered too — so the **RPG Series** mods work out of the box:

**[Archers](https://modrinth.com/mod/archers)** · **[Wizards](https://modrinth.com/mod/wizards)** · **[Paladins & Priests](https://modrinth.com/mod/paladins-and-priests)** · **[Rogues & Warriors](https://modrinth.com/mod/rogues-and-warriors)**

Your arms hold the cast pose, and the footwork shows on your legs while standing still. Melee weapons from those mods are handled by the normal Better Combat support above. 

`This also covers other animations played through Player Animator as a side effect of the new method.
This feature will be migrated to the core in the future as "baseline compatibility."`

## Config

Open the in-game config screen (Mods → EMF Compat Core → Config) and pick the **Better Combat** tab:

| Option | What it does                                                                                                                                                                  |
|---|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EMF compatibility | Master switch — turn the whole addon off to get plain Better Combat behaviour.                                                                                                |
| Arm sync | **Body-follow** keeps attack poses attached to your moving torso. **Rotation-only** is the older, simpler behaviour, but in some cases it gives smoother animations. |
| Attack legs | Shows the attack/cast footwork on your legs while standing still. Turn off to leave legs to the resource pack.                                                                |
| RPG Series & other animations | Covers Spell Engine casts and other Player Animator animations. Only active when Spell Engine is installed.                                                                   |

## Additional Compatibility

**[Player Animator](https://modrinth.com/mod/playeranimator)** — used by Better Combat under the hood.

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — attack poses stay correct even when the camera is detached.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — swings stay visible on your body in first person.

## Build

```bash
./gradlew :better-combat-neoforge-1.21.1:build
./gradlew :better-combat-fabric-1.21.11:build
```

enjoy ^_^
