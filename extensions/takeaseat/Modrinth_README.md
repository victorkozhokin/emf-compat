# EMF Compat: Take a Seat

![Sit](https://cdn.modrinth.com/data/npIU8sEo/images/b4049d7e82ed9288114e2d86ce049671914dd2fd.gif)

A small client-side mod that makes **[Take a Seat](https://modrinth.com/mod/take-a-seat)** sitting poses work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

Without it, you sit down and your character keeps standing — the resource-pack animation plays right through the chair.

## Covered Poses

| Pose | Captured parts |
|---|---|
| Sitting on a chair or bench | Whole body except the head |

The head stays under EMF's control, so you can still look around naturally while seated.

## Features

- Compatible with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**.
- Sitting poses stay visible instead of being overwritten by the resource-pack animation.
- Works for other players too, so everyone actually sits.

## On Fabric 1.21.11+

Entity Model Features already pauses its own animations while Take a Seat plays its pose, so sitting itself looks right without this addon. What it still fixes there is the **armour** — chestplate, leggings and boots follow the seated pose instead of staying in a standing position.

## Known issues

On 1.21.11+, armour can sit slightly loose on the body while you move or crouch, so skin layers may poke through it. 
This comes from how EMF sizes the armour model and is not specific to this addon.

enjoy ^_^