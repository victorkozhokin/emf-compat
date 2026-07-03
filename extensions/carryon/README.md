# Carry On: EMF Compat

A small client-side mod that pauses **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player animations while **[Carry On](https://modrinth.com/mod/carry-on)** is carrying a block or entity.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

**Also keeps carried objects synced to the animated torso!**

## Features

- Pauses EMF player animations while carrying
- Preserves the raised-arm carry pose
- Syncs carried blocks/entities to the EMF-animated torso in third person
- Renders carried entities with the vanilla model
- Compatible with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**
- Should work with most player animation resource packs using EMF

## Additional Compatibility

**[Corpse](https://modrinth.com/mod/corpse)** — Corpse dummy players/skeletons are excluded from Carry On tracking.

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — pose capture/restore continues while the camera is detached.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — the carry pose stays visible on the visible body in first person.

## Build

```bash
./gradlew :carryon-neoforge-1.21.1:build
./gradlew :carryon-forge-1.20.1:build
./gradlew :carryon-fabric-1.21.11:build
```

enjoy ^_^
