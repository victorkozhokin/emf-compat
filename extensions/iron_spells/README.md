# EMF Compat: Iron's Spells 'n Spellbooks

## [Modrinth](https://modrinth.com/project/emf-compat-irons-spells-n-spellbooks)

A small client-side mod that makes **[Iron's Spells 'n Spellbooks](https://modrinth.com/mod/irons-spells-n-spellbooks)** casting poses work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

Without it, spellcasting looks like nothing is happening — the resource-pack animation keeps your arms in their idle motion while you cast.

## Covered Poses

| Pose | Captured parts |
|---|---|
| Casting a spell | Both arms |

The head, body and legs always stay under EMF's control, so resource-pack animations keep playing while you cast.

## Features

- Casting poses stay visible instead of being overwritten by the resource-pack animation.
- Arms follow your moving torso, so the pose stays attached to your body while you walk.
- Casting stays visible in first person, so your own hands match the spell you're casting.
- Works for other players too — you can see what everyone is casting.
- Body and legs keep their resource-pack animations while casting.

## Config

Open the in-game config screen (Mods → EMF Compat Core → Config) and pick the **Iron's Spells** tab:

| Option | What it does |
|---|---|
| EMF compatibility | Master switch — turn the whole addon off to get plain Iron's Spells behaviour. |
| Arm sync | **Body-follow** keeps the casting pose attached to your moving torso. **Rotation-only** is the older, simpler behaviour, but in some cases it gives smoother animations. |

## Dependencies

- [Iron's Spells 'n Spellbooks](https://modrinth.com/mod/irons-spells-n-spellbooks)
- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.2.4+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)
- EMF Compat Core 1.0.1+

## Supported loaders / versions

| Loader | Minecraft versions |
|--------|-------------------|
| NeoForge | 1.21.1 |

## Build

```bash
./gradlew :iron-spells-neoforge-1.21.1:build
```

enjoy ^_^
