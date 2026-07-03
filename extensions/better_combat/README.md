# Better Combat: EMF Compat

A small client-side mod that pauses **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player animations while a **[Better Combat](https://modrinth.com/mod/better-combat)** attack is playing.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

**Also tones down the mace slam torso tilt!**

## Features

- Pauses EMF player animations during Better Combat attacks
- Preserves raised/charging arm and weapon-swing poses
- Works with one-handed, two-handed and mace attacks
- Compatible with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**
- Should work with most player animation resource packs using EMF

## Additional Compatibility

**[Player Animator](https://modrinth.com/mod/playeranimator)** — used internally by Better Combat; poses are handled through the shared animation system.

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — pose capture/restore continues while the camera is detached.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — attack poses stay visible on the visible body in first person.

## Build

```bash
./gradlew :better-combat-neoforge-1.21.1:build
./gradlew :better-combat-fabric-1.21.11:build
```

enjoy ^_^
