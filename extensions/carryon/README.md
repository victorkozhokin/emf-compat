# EMF Compat: Carry On

## [Modrinth](https://modrinth.com/project/emf-compat-carry-on)

A small client-side mod that makes **[Carry On](https://modrinth.com/mod/carry-on)** carry poses work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

## Features

- Your arms stay raised naturally while carrying blocks and entities.
- Carried objects follow your EMF-animated body in third person.
- Carried entities keep their normal model so they don't inherit the player resource-pack shape.
- First-person carry pose stays visible.
- Compatible with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**.
- Should work with most player animation resource packs using EMF.

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — carry poses stay correct even when the camera is detached.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — raised arms stay visible on your body in first person.

## Build

```bash
./gradlew :carryon-neoforge-1.21.1:build
./gradlew :carryon-forge-1.20.1:build
./gradlew :carryon-fabric-1.21.11:build
```

enjoy ^_^
