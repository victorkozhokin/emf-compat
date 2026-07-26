# EMF Compat: Gliders

A small client-side mod that makes **[Paragliders](https://modrinth.com/mod/paragliders)** gliding poses work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** but it should work with any player animation resource pack.

## Features

- Arms and legs keep the gliding pose over EMF animations while paragliding.
- The head stays under EMF control, so head tracking keeps working while gliding.
- Works for remote players too (uses the Paragliders movement API / item state).
- Should work with most player animation resource packs using EMF.

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — gliding pose stays correct even when the camera is detached.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — gliding pose stays visible on your body in first person.

## Build

```bash
./gradlew :gliders-neoforge-1.21.1:build
./gradlew :gliders-forge-1.20.1:build
```

enjoy ^_^
