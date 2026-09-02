# EMF Compat: ParCool

A small client-side mod that makes **[ParCool!](https://modrinth.com/mod/parcool)** parkour animations work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

Without it, ParCool's moves are lost the moment EMF takes over the model: you vault a fence and your character keeps jogging on the spot, arms swinging to the resource pack's idle. This addon keeps the parkour pose where it belongs.

## Covered Poses

Every ParCool animation is covered — vaults, wall runs and wall jumps, rolls and breakfalls, climbing and hanging, sliding, crawling, dodges and dives.

| ParCool version | Captured parts |
|---|---|
| 4.x | Exactly the parts the running action animates |
| 3.4.x, action owns the model | Head, torso, arms and legs |
| 3.4.x, action adjusts the vanilla pose | Head, arms and legs |

ParCool 4 reports which limbs each action drives, so anything it does not touch keeps playing the resource pack's animation. ParCool 3 has no such list, so the addon falls back to the scope its two animation modes imply.

## Features

- Parkour moves stay visible in third person instead of falling back to the resource-pack animation.
- Works for other players too, so everyone's parkour looks right.
- Works with both ParCool generations — 3.4.x and 4.x — and picks the right path automatically.
- Your own first-person view is left untouched.
- Body rotation during flips and dives is ParCool's own and was never affected by EMF; it keeps working as before.

## Config

Open the in-game config screen (Mods → EMF Compat Core → Config) and pick the **ParCool** tab:

| Option | What it does |
|---|---|
| EMF compatibility | Master switch — turn the whole addon off to get plain ParCool behaviour. |
| Pose scope | **Whole pose** holds every part ParCool animates. **Limbs only** leaves the head and torso to the resource pack, so facial and idle animations keep playing during a move. |

## Dependencies

- [ParCool!](https://modrinth.com/mod/parcool) 3.4.0.0+ (3.4.x or 4.x)
- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.2.4+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)
- EMF Compat Core 1.1.0+

## Notes

ParCool's own [compatibility addon](https://github.com/semillakan6/ParCool-CompatibilityAddon-NeoForge) solves the same clash the other way round: it asks EMF to drop to the vanilla model and pause its animation while ParCool poses the player. That works, but it costs you the pack's animation for as long as the move lasts. This addon captures ParCool's pose and replays it over the EMF model instead, so the rest of your pack keeps running. Running both at once is redundant — pick one.

## Supported loaders / versions

| Loader | Minecraft versions |
|--------|-------------------|
| NeoForge | 1.21.1 |

## Build

```bash
./gradlew :parcool-neoforge-1.21.1:build
```

enjoy ^_^
