# EMF Compat: Corpse

A small client-side mod for **Minecraft** that keeps **[Corpse](https://modrinth.com/mod/corpse)** death bodies still when **Entity Model Features** player models are active.

## What it does

Corpse renders dead bodies by creating internal "dummy" players and skeletons and drawing them through the normal renderer. Because EMF replaces that model with a shared custom model, simply pausing animation leaves the corpse stuck in the real player's pose. This addon temporarily switches the renderer to the vanilla EMF variant (`0`) during the corpse render pass, so bodies keep Corpse's intended pose and don't twitch or walk.

## Supported versions

| Loader | Minecraft versions |
|--------|-------------------|
| NeoForge | 1.21.1 |
| Forge | 1.20.1 |

## Dependencies

**Forge**
- [Forge](https://files.minecraftforge.net/) 47.3+
- [Corpse](https://modrinth.com/mod/corpse) for Forge 1.20.1

**NeoForge**
- [NeoForge](https://neoforged.net/) 21.1+
- [Corpse](https://modrinth.com/mod/corpse) for NeoForge 1.21.1

**General**
- [EMF Compat Core](../../core/README.md)
- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.2.4+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)

## Build

```bash
./gradlew :corpse-neoforge-1.21.1:build
./gradlew :corpse-forge-1.20.1:build
```
