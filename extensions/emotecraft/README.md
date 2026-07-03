# Emotecraft: EMF Compat

A small client-side mod that pauses **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player animations while **[Emotecraft](https://modrinth.com/mod/emotecraft)** is playing an emote.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

**Also preserves only the body parts that the emote actually moves!**

## Features

- Pauses EMF player animations during Emotecraft emotes
- Preserves wave, salute, dance and custom emote poses
- Captures only the body parts used by the emote
- Compatible with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**
- Should work with most player animation resource packs using EMF

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — pose capture/restore continues while the camera is detached.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — emote poses stay visible on the visible body in first person.

## Build

```bash
./gradlew :emotecraft-neoforge-1.21.1:build
```

enjoy ^_^
