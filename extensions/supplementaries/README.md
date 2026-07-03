# EMF Compat: Supplementaries

## [Modrinth](https://modrinth.com/project/emf-compat-supplementaries)

A small client-side mod that makes **[Supplementaries](https://modrinth.com/mod/supplementaries)** item-use poses work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

## Features

- Your arms keep the right pose while playing the flute, aiming the slingshot or blowing bubbles.
- Item-use animations don't get overridden by idle resource-pack animations.
- First-person hand pose stays visible.
- Compatible with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**.
- Should work with most player animation resource packs using EMF.

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — item poses stay correct even when the camera is detached.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — item poses stay visible on your body in first person.

## Build

```bash
./gradlew :supplementaries-neoforge-1.21.1:build
./gradlew :supplementaries-forge-1.20.1:build
```

enjoy ^_^
