# EMF Compat: Not Enough Animations

A small client-side mod that makes **[Not Enough Animations](https://modrinth.com/mod/not-enough-animations)** animations work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

Not Enough Animations adds extra player animations for eating, rowing, riding and more. This addon lets those animations take priority over EMF resource-pack animations.

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

- **[Freecam](https://modrinth.com/mod/freecam)** — NEA animations stay correct even when the camera is detached.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — NEA animations stay visible on your body in first person.

enjoy ^-_-^
