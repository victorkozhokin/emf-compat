# Immersive Melodies: EMF Compat

A small client-side mod that pauses **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player animations while **[Immersive Melodies](https://modrinth.com/mod/immersive-melodies)** is playing an instrument.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

**Also preserves flute, slingshot and bubble blower poses!**

## Features

- Pauses EMF player animations during instrument use
- Preserves instrument-holding and playing arm poses
- Works with all Immersive Melodies instruments
- Compatible with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**
- Should work with most player animation resource packs using EMF

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — pose capture/restore continues while the camera is detached.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — instrument poses stay visible on the visible body in first person.

## Build

```bash
./gradlew :immersive-melodies-neoforge-1.21.1:build
./gradlew :immersive-melodies-forge-1.20.1:build
```

enjoy ^_^
