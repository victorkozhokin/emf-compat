# EMF Compat: Horse Sync

A client-side **NeoForge 1.21.1** addon that synchronizes the player's riding position with horses animated by **Entity Model Features**.

## What it does

When a player rides a horse whose body is animated by EMF, the player's in-world position can become misaligned with the moving torso. This addon reads the animated-vs-base Y offset of the horse's body part and translates the player render up or down to match.

## Supported versions

| Loader | Minecraft versions |
|--------|-------------------|
| NeoForge | 1.21.1 |

## Dependencies

- [NeoForge](https://neoforged.net/) 21.1+
- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.2.4+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)

This addon does **not** require EMF Compat Core.

## Configuration

The mod has one client-side config option:

| Option | Default | Description |
|--------|---------|-------------|
| `enabled` | `true` | Enables/disables the horse riding-position sync. |

## Build

```bash
./gradlew :horse-sync-neoforge-1.21.1:build
```
