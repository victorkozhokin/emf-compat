# EMF Compat: Not Enough Animations

## [Modrinth](https://modrinth.com/mod/not-enough-animations-emf-compat)

A small client-side mod for **Minecraft** that pauses **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player animations while **[Not Enough Animations](https://modrinth.com/mod/not-enough-animations)** is active.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** but it should work with any player-animation resource pack.

## Video

[![NEA EMF Compat](https://img.youtube.com/vi/QIZlt0xBARQ/maxresdefault.jpg)](https://youtu.be/QIZlt0xBARQ?si=IvY1qwvvB6avab2r)

## Covered animations (EMF yields to NEA)

| Animation | Notes |
|-----------|-------|
| Boat (rowing) | |
| Horse (riding) | |
| Eating / Drinking | |
| Hug | |
| Item Swap | |
| Petting | |
| Map Holding | |
| Hold Up Items | Lanterns, etc. |
| Burning | Works even while sprinting |
| Freezing | Works even while sprinting |
| Naruto Running | Works only while sprinting |

## Ignored animations (Fresh Animations: Player Extension handles them better)

| Animation | Reason |
|-----------|--------|
| Ladder | FA:PE handles ladders better |
| Crawling | FA:PE handles crawling better |
| Elytra | FA:PE handles elytra better |
| Falling | FA:PE handles falling better |
| Death | FA:PE handles death better |
| Swimming | FA:PE handles swimming better |
| Bow / Crossbow | FA:PE handles bow/crossbow better; excluded intentionally due to broken behaviour |

## Sprint behavior

The compat layer is **disabled while sprinting**, with the exception of `Burning` and `Freezing` animations.

## Recommended "Not Enough Animations" settings

These settings are in the **Not Enough Animations** mod config. This mod has no config of its own.

| Setting | Value |
|---------|-------|
| Animation Smoothing | OFF |
| Disable Leg Smoothing | ON |
| Ladder Animation | OFF |
| Bow Animation | Vanilla |

## Dependencies

- [NeoForge](https://neoforged.net/) 21.1+
- [Not Enough Animations](https://modrinth.com/mod/not-enough-animations) 1.8+
- [EMF Compat Core](../../core/README.md)
- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.2.4+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — when the camera is detached, the compat layer keeps capturing and restoring poses so animations stay correct.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — when FPM is installed and enabled, pose capture and restoration are not skipped in first person, so NEA animations still apply to the visible body.

## Known Limitations

> The mod uses a pose save/restore workaround instead of the official EMF `pauseCustomAnimationsForThesePartsOfEntity` API. The API may not work correctly with ASM math compilation enabled (default in EMF 3.2.4+), though this is only a suspected issue on our end.

## Build

```bash
./gradlew :nea:build
```
