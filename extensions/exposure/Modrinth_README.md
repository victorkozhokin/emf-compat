# EMF Compat: Exposure

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

- Compatible with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**.
- Camera poses stay visible instead of being overwritten by resource-pack animations.
- Works for remote players too — the camera state is synced by Exposure.
- The head keeps following the EMF-animated body, so the hat/hair doesn't detach.
- Should work with most player animation resource packs using EMF.

enjoy ^_^
