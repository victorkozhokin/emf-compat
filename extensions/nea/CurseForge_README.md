# Not Enough Animations: EMF Compat

A small client-side mod that pauses **[Entity Model Features](https://www.curseforge.com/minecraft/mc-mods/entity-model-features)** player animations while **[Not Enough Animations](https://www.curseforge.com/minecraft/mc-mods/not-enough-animations)** is active.

Tested with **[Fresh Animations: Player Extension](https://www.curseforge.com/minecraft/texture-packs/fa-player-extension)** and **[Detailed Animations](https://www.curseforge.com/minecraft/texture-packs/detailed-animations)** but it should work with any player animation resource pack.

## Video

<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/QIZlt0xBARQ" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>

## Covered Animations

| Animation | Notes |
|---|---|
| Boat (rowing) | |
| Horse (riding) | |
| Eating / Drinking | |
| Hug | |
| Item Swap | |
| Petting | |
| Map Holding | |
| Hold Up Items | Can be disabled for FA:PE |
| Burning | Works even while sprinting |
| Freezing | Works even while sprinting |
| Naruto running | Works only while sprinting |

## Ignored Animations

| Animation | Reason |
|---|---|
| Ladder | FA:PE handles ladders better |
| Crawling | FA:PE handles crawling better |
| Elytra | FA:PE handles elytra better |
| Falling | FA:PE handles falling better |
| Death | FA:PE handles death better |
| Swimming | FA:PE handles swimming better |
| Bow / Crossbow | FA:PE handles bow/crossbow better |

## Sprint Behavior

The compat layer is **disabled while sprinting**, with the exception of `BurningAnimation` and `FreezingAnimation`.

## REQUIRED "Not Enough Animations" Settings

These settings are REQUIRED to set in the mod **Not Enough Animations**.

| Setting | Value |
|---|---|
| Animation Smoothing | OFF |
| Disable Leg Smoothing | ON |
| Ladder Animation | OFF |
| Bow Animation | Vanilla |

## Compatibility

- **[Freecam](https://www.curseforge.com/minecraft/mc-mods/free-cam)** — when the camera is detached, the compat layer keeps capturing and restoring poses so animations stay correct.
- **[First Person Model](https://www.curseforge.com/minecraft/mc-mods/first-person-model)** — when FPM is installed and enabled, pose capture and restoration are not skipped in first person, so NEA animations still apply to the visible body.

enjoy ^-_-^