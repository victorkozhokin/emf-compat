# EMF Compat: ParCool

A small client-side mod that makes **[ParCool!](https://modrinth.com/mod/parcool)** parkour animations work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

Without it, ParCool's moves are lost the moment EMF takes over the model: you vault a fence and your character keeps jogging on the spot, arms swinging to the resource pack's idle.

## Covered Poses

Every ParCool animation — vaults, wall runs and wall jumps, rolls and breakfalls, climbing and hanging, ziplines, sliding, crawling, dodges and dives.

| ParCool version | Captured parts |
|---|---|
| 4.x | Exactly the parts the running action animates |
| 3.4.x, action owns the model | Head, torso, arms and legs |
| 3.4.x, action adjusts the vanilla pose | Head, arms and legs |

The body rotation of flips and dives is ParCool's own and was never affected by EMF.

## Features

- Parkour moves stay visible in third person instead of falling back to the resource-pack animation, for other players too.
- Works with both ParCool generations — 3.4.x and 4.x — and picks the right path automatically.
- On ParCool 4 moves fade in and out of the pack's animation at ParCool's own pace instead of snapping.
- Attack, play or eat while running: Better Combat, Immersive Melodies and Not Enough Animations get the arms, the legs keep running (ParCool 4).
- Only affects third-person rendering; your own first-person view is left untouched.

## Fresh Animations module

ParCool's keyframed poses sit oddly next to a procedural pack, so the jar also ships an animation module for FA+Player, made with FreshLX's permission: the fast run and charge jump, hands that rest on a ledge or hold a bar (with hand-over-hand shimmying and brachiation), FA's own crawl, swim and ladder climb for ParCool's crawl, fast swim and pole climb, and a cape that stays on the back. It is in the resource pack list as **EMF Compat: ParCool Animations** and goes **above FA+Player**, which it needs under it; it is not turned on by itself.

Generated from a copy of FA+Player, so rebuild it whenever FA+Player is updated:

```bash
# the copy inside the jar
python3 extensions/parcool/resourcepack/build_pack.py <FA+Player zip> extensions/parcool/neoforge/1.21.1/src/main/resources/resourcepacks --builtin
# or a loose pack for a resourcepacks folder
python3 extensions/parcool/resourcepack/build_pack.py <FA+Player zip> <resourcepacks folder>
```

Any pack can animate the moves itself: on ParCool 4 the addon publishes ParCool's state as EMF animation variables, each registered with a one-line description in `ParCoolPackVariables.java`. Reading a move's variables takes that move over; moves a pack does not read keep ParCool's poses, so a pack is turned off the usual way - in the resource pack list.

## Config

Open the in-game config screen (Mods → EMF Compat Core → Config) and pick the **ParCool** tab:

| Option | What it does |
|---|---|
| EMF compatibility | Master switch — turn the whole addon off to get plain ParCool behaviour. |
| Pose scope | **Whole pose** holds every part ParCool animates. **Limbs only** leaves the head and torso to the resource pack. |

## Dependencies

- [ParCool!](https://modrinth.com/mod/parcool) 3.4.0.0+ (3.4.x or 4.x)
- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.2.4+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)
- EMF Compat Core 2.3.0+

## Supported loaders / versions

| Loader | Minecraft versions |
|--------|-------------------|
| NeoForge | 1.21.1 |

## Build

```bash
./gradlew :parcool-neoforge-1.21.1:build
```

enjoy ^_^
