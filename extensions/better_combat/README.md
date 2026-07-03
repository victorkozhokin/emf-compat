# EMF Compat: Better Combat

## [Modrinth](https://modrinth.com/project/emf-compat-better-combat)

A small client-side mod that makes **[Better Combat](https://modrinth.com/mod/better-combat)** attack animations work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

## Features

- Your weapon swings and charge poses look right instead of being overwritten by idle resource-pack animations.
- Body and legs keep their EMF animations during attacks; only arms switch to Better Combat.
- Held items align with your swing in first person, so weapons don't float next to your arm.
- Smooth hand-off back to EMF at the end of an attack — no single-frame snap.
- Works with one-handed, two-handed and mace attacks.
- Compatible with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**.
- Should work with most player animation resource packs using EMF.

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
