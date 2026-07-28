# EMF Compat: Exposure

## [Modrinth](https://modrinth.com/project/emf-compat-exposure)

A small client-side mod that makes **[Exposure](https://modrinth.com/mod/exposure)** camera poses work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

## Covered Poses

| Pose | Captured parts |
|---|---|
| Aiming (camera raised to the face) | Both arms + head |
| Selfie | Only the arm holding the camera |
| Tripod camera (stand) | Both arms + head |
| Disassembled camera in hand (attachment UI) | Both arms + head |

The body and legs always stay under EMF's control, so resource-pack walk/idle animations keep playing while you use a camera.

## Features

- Camera poses stay visible instead of being overwritten by resource-pack animations.
- Works for remote players too — the camera state is synced by Exposure.
- Respects the player's main-hand setting for the selfie pose.
- Only affects third-person rendering; your own first-person view is left untouched.

## Dependencies

- [Exposure](https://modrinth.com/mod/exposure) 1.9+
- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.2.4+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)
- EMF Compat Core 1.0.1+

## Config

Open the in-game config screen (Mods → EMF Compat Core → Config) and pick the **Exposure** tab:

| Option | What it does |
|---|---|
| EMF compatibility | Master switch — turn the whole addon off to get plain Exposure behaviour. |
| Arm sync | **Body-follow** keeps captured poses attached to your moving torso. **Rotation-only** is the older, simpler behaviour, but in some cases it gives smoother animations. |

## Build

```bash
./gradlew :exposure-neoforge-1.21.1:build
./gradlew :exposure-forge-1.20.1:build
```

enjoy ^_^
