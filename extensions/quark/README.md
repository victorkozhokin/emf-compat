# EMF Compat: Quark

A small client-side mod that makes **[Quark](https://modrinth.com/mod/quark)** emotes work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

## Features

- Wave, salute, dance and custom Quark emotes play correctly over EMF animations.
- Only the body parts used by the emote change; the rest keeps its resource-pack animation.
- Compatible with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**.
- Should work with most player animation resource packs using EMF.

## Additional Compatibility

**[Zeta](https://modrinth.com/mod/zeta)** — required library for Quark.

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — emote poses stay correct even when the camera is detached.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — emote poses stay visible on your body in first person.

## Build

```bash
./gradlew :quark-neoforge-1.21.1:build
```

enjoy ^_^
