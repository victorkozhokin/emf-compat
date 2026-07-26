# EMF Compat: WATUT

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

- Compatible with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**.
- GUI / typing / idle poses survive EMF resource-pack animations.
- Respects WATUT's own config toggles — features disabled in WATUT stay disabled.
- Smooth transitions — WATUT's own pose lerp plays out instead of snapping.
- Works for remote players too — statuses are synced by WATUT.
- Should work with most player animation resource packs using EMF.

enjoy ^_^
