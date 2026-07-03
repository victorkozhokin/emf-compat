# Not Enough Animations: EMF Compat

A small client-side mod that pauses **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player animations while **[Not Enough Animations](https://modrinth.com/mod/not-enough-animations)** is active.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

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

- **[Freecam](https://modrinth.com/mod/freecam)** — when the camera is detached, the compat layer keeps capturing and restoring poses so animations stay correct.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — when FPM is installed and enabled, pose capture and restoration are not skipped in first person, so NEA animations still apply to the visible body.

## Build

```bash
./gradlew :nea-neoforge-1.21.1:build
./gradlew :nea-forge-1.20.1:build
./gradlew :nea-fabric-1.21.11:build
./gradlew :nea-fabric-1.21.4:build
./gradlew :nea-fabric-26.1.2:build
./gradlew :nea-fabric-26.2:build
```

enjoy ^-_-^
