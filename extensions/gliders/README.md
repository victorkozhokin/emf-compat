# EMF Compat: Gliders

A small client-side mod that makes gliding poses work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Supports three glider mods — **[Paragliders](https://modrinth.com/mod/paragliders)**, **[Gliders](https://modrinth.com/mod/gliders)** and **[Reliable Gliders](https://modrinth.com/mod/reliable-gliders)** — any combination of them, each with its own toggle.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** but it should work with any player animation resource pack.

## Features

- Your gliding pose stays visible instead of being overwritten by the resource pack.
- If your pack has a flying animation, it plays while you glide — with all three glider mods.
- The glider's opening animation plays in full: your body and legs swing under it, and only then do you settle into the flying pose.
- The head stays under EMF control, so head tracking keeps working while gliding.
- Works for other players too, not just you.
- Should work with most player animation resource packs using EMF.

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — gliding pose stays correct even when the camera is detached.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — gliding pose stays visible on your body in first person.

## Config

Open the in-game config screen (Mods → EMF Compat Core → Config) and pick the **Gliders** tab. Only the glider mods you actually have installed get a row:

| Option | What it does |
|---|---|
| EMF compatibility | Master switch — turn the whole addon off to get plain gliders behaviour. |
| Arm sync | **Body-follow** keeps captured poses attached to your moving torso. **Rotation-only** is the older, simpler behaviour, but in some cases it gives smoother animations. |
| Paragliders | Hold the paragliding pose, and play your pack's flight animation while gliding. |
| Gliders (vc) | Play the glider's opening animation in full, then hold the gliding pose and your pack's flight animation. |
| Reliable Gliders | Hold the Reliable Gliders pose, and play your pack's flight animation while gliding. |

## Build

```bash
./gradlew :gliders-neoforge-1.21.1:build
./gradlew :gliders-forge-1.20.1:build
```

enjoy ^_^
