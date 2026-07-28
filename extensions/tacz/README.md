# EMF Compat: TACZ

## [Modrinth](https://modrinth.com/project/emf-compat-tacz)

A small client-side mod that makes **[Timeless and Classics Zero](https://modrinth.com/mod/timeless-and-classics-zero)** gun poses work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

Without it, your character keeps playing the resource-pack idle animation while holding a gun — arms swinging as if empty-handed. This addon keeps the gun in your hands where it belongs.

## Covered Poses

| Pose | Captured parts |
|---|---|
| Holding a gun | Both arms |
| Aiming down sights | Both arms + head |
| Reloading | Both arms |

The body and legs always stay under EMF's control, so resource-pack walk and idle animations keep playing while you hold a gun.

## Features

- Your hands stay on the gun instead of falling back to the resource-pack idle animation.
- Aiming, reloading and shooting poses stay visible in third person.
- The head follows your aim while TACZ animates it, and keeps its resource-pack motion otherwise.
- Arms follow your moving torso, so the gun doesn't drift away from your body while you walk.
- Works for other players too, so everyone holds their guns properly.
- Your own first-person view is left untouched.

## Config

Open the in-game config screen (Mods → EMF Compat Core → Config) and pick the **TACZ** tab:

| Option | What it does |
|---|---|
| EMF compatibility | Master switch — turn the whole addon off to get plain TACZ behaviour. |
| Arm sync | **Body-follow** keeps the gun pose attached to your moving torso. **Rotation-only** is the older, simpler behaviour. |

## Dependencies

- [Timeless and Classics Zero](https://modrinth.com/mod/timeless-and-classics-zero)
- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.2.4+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)
- EMF Compat Core 1.0.1+

## Supported loaders / versions

| Loader | Minecraft versions |
|--------|-------------------|
| NeoForge | 1.21.1 |

## Build

```bash
./gradlew :tacz-neoforge-1.21.1:build
```

enjoy ^_^
