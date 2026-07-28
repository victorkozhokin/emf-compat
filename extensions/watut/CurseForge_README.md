# EMF Compat: WATUT

A small client-side mod that makes **[What Are They Up To](https://www.curseforge.com/minecraft/mc-mods/what-are-they-up-to-watut)** status poses work correctly with **[Entity Model Features](https://www.curseforge.com/minecraft/mc-mods/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://www.curseforge.com/minecraft/texture-packs/fa-player-extension)** and **[Detailed Animations](https://www.curseforge.com/minecraft/texture-packs/detailed-animations)** but it should work with any player animation resource pack.

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
- Works for remote players too — statuses are synced by WATUT.
- Should work with most player animation resource packs using EMF.

enjoy ^_^
