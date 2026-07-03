# Supplementaries: EMF Compat

A small client-side mod that pauses **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player animations while using **[Supplementaries](https://modrinth.com/mod/supplementaries)** items.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

**Also keeps first-person hand poses visible!**

## Features

- Pauses EMF player animations while playing the Flute
- Pauses EMF player animations while aiming the Slingshot
- Pauses EMF player animations while using the Bubble Blower
- Preserves two-handed and one-handed item-use arm poses
- Compatible with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**
- Should work with most player animation resource packs using EMF

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — pose capture/restore continues while the camera is detached.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — item poses stay visible on the visible body in first person.

## Build

```bash
./gradlew :supplementaries-neoforge-1.21.1:build
./gradlew :supplementaries-forge-1.20.1:build
```

enjoy ^_^
