# EMF Compat: Exposure

![Kitty](https://cdn.modrinth.com/data/mHqWFw6Z/images/d53d779ef2229f141e16db690df60d6b2bce82ff.gif)

A small client-side mod that makes **[Exposure](https://modrinth.com/mod/exposure)** camera poses work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

## Covered Poses

| Pose | Captured parts |
|---|---|
| Aiming (camera raised to the face) | Both arms + head |
| Selfie | Only the arm holding the camera |

The body and legs always stay under EMF's control, so resource-pack walk/idle animations keep playing while you use a camera.

## Features

- Compatible with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**.
- Camera poses stay visible instead of being overwritten by resource-pack animations.
- Works for remote players too - the camera state is synced by Exposure.
- Should work with most player animation resource packs using EMF.

enjoy ^_^