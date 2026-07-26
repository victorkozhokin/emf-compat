# EMF Compat: WATUT

## [Modrinth](https://modrinth.com/project/emf-compat-watut)

A small client-side mod that makes **[What Are They Up To](https://modrinth.com/mod/what-are-they-up-to)** status poses work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

## Covered Poses

| Pose | Captured parts |
|---|---|
| GUI (player is looking at a screen) | Arms + head |
| Typing in chat | Arms |
| Idle (head droop) | Head |

The body and legs always stay under EMF's control, so resource-pack animations keep playing while WATUT poses the arms and head.

## Features

- GUI / typing / idle poses survive EMF resource-pack animations.
- Respects WATUT's own config toggles — features disabled in WATUT stay disabled.
- Smooth transitions — WATUT's own pose lerp plays out instead of snapping.
- The player doll in your own inventory stays untouched, same as in WATUT.
- Works for remote players too — statuses are synced by WATUT.

## Dependencies

- [What Are They Up To](https://modrinth.com/mod/what-are-they-up-to) 1.2.1+ (1.20.1) / 1.2.3+ (1.21.1)
- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.2.4+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)
- EMF Compat Core 1.0.1+

## Build

```bash
./gradlew :watut-neoforge-1.21.1:build
./gradlew :watut-forge-1.20.1:build
```

enjoy ^_^
