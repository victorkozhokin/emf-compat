# EMF Compat: Horse Sync

## [Modrinth](https://modrinth.com/project/emf-compat-horse-sync)

A small client-side mod that keeps you seated properly on horses animated by **[Entity Model Features](https://modrinth.com/mod/entity-model-features)**.

Tested with **[Fresh Animations](https://modrinth.com/resourcepack/fresh-animations)** and **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** but it should work with any animated horse resource pack.

When a resource pack animates the horse, its body bobs up and down as it moves — but the rider stays at a fixed height, so you appear to sink into the saddle or float above it. This addon makes you rise and fall together with the horse.

## Features

- You stay glued to the saddle while the horse running.
- Works with any resource pack that animates horses through EMF.
- Applies to other players on their horses too.
- Optional riding pose: legs to the sides, hands on the reins, leaning forward with the gait.

> The riding pose is **experimental** — if it doesn't suit your resource pack, turn it off in the config and keep just the height sync.

## Config

Open the in-game config screen (Mods → EMF Compat Core → Config) and pick the **Horse Sync** tab:

| Option | What it does |
|---|---|
| EMF compatibility | Master switch — turn the whole addon off. |
| Riding animation | Adds the riding pose described above. Turn off to keep your resource pack's own mounted pose. |

## Dependencies

- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.2.4+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)
- EMF Compat Core 1.0.1+

## Supported loaders / versions

| Loader | Minecraft versions |
|--------|-------------------|
| NeoForge | 1.21.1 |

## Build

```bash
./gradlew :horse-sync-neoforge-1.21.1:build
```

enjoy ^_^
