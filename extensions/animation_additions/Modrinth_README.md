# EMF Compat: Animation Additions

A client-side mod that adds animations of its own on top of an **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player model: feet that stand on the ground they are on, hands that go to the thing you use, and a body that takes part in what the hands do.

Made for and tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**. The pack keeps its own walk, idle motion and expressions; this addon only adds to them. Other player animation packs may give different results.

**Experimental:** an early version — things may still change.

## Features

- Feet stand on stairs, slabs and uneven ground; a careful stance on fences and other narrow supports.
- Hands go to the thing you use: buttons, levers, doors, chests, workstations and dozens of other blocks.
- The whole body takes part: the torso leans, the feet step into a stance, the weight shifts.
- Mining swings the tool on to the block, with a stance for each tool and three different blows.
- Feeding, milking and shearing by hand; dressing an armour stand; planting seeds; looking through an open chest.
- Putting on armour and Curios accessories; shaking off water, snow and mud; pocketing what was picked up.
- Cranks, valves, steering wheels, throttles and typewriters of Create, Aeronautics and Supplementaries are worked with both hands — also seated in a cockpit and on a ship under way.
- You stay on the saddle of an animated horse.
- Works for other players too.
- Every animation has its own switch in the config.

## Everything it does

Foot IK on stairs, slabs and uneven ground, with the step up prepared ahead; balance on fences, walls, bars and slopes; torso lean and pose inertia; turning the body to fit a narrow passage; a hand on the wall beside you; hands brushing through plants; a glance at a nearby creature; holding an animal's lead with the body against the pull; one pocketing gesture after a run of pickups; pressing buttons and levers at their real height, standing, crouched or seated; reaching past arm's length for a control; a hand on doors and gates as they open; lids, pages and work slots of blocks used by hand; mining with a stance for each tool and three different blows; feeding with a stroke after, milking and shearing; the hand held out before the click; dressing an armour stand; planting seeds along a row; looking through an open container; putting on a helmet, a chestplate, leggings and boots, and Curios accessories; shaking off water, powder snow and mud; these gestures on other players; turning a crank or a valve with the whole body; a steering wheel held hand over hand; a seated cockpit with the wheel in one hand and a throttle or typewriter in the other; bracing on moving transport and holding a rope at the edge; the weighted ejector's launch and flight; staying on the saddle of an animated horse, with an optional riding pose; contacts that follow a moving Sable sub-level.

## Blocks you can interact with

<details>
<summary>Show the list</summary>

**Vanilla:** buttons, levers, doors, fence gates, chests, barrels, lectern, chiseled bookshelf, jukebox, note block, composter, cauldron, beehive and bee nest, respawn anchor, cake, candles and candle cake, flower pot, repeater, comparator, daylight detector, enchanting table, cartography table, crafting table, stonecutter, crafter, vault, bell, TNT, campfire.

**Create:** hand crank, valve handles, weighted ejector, train controls, contraption controls, factory gauge, content observer, track station, depot, basin, item drain, blaze burner, mechanical arm, deployer, mechanical crafter, package frogport, packager and repackager, postbox, rotation speed controller, sequenced gearshift, stockpile switch, display link, redstone link, elevator contact, stock ticker, redstone requester, funnels, smart chute, creative motor, mechanical bearing.

**Create Aeronautics:** steering wheel, throttle lever, linked typewriter, navigation table, physics assembler, mounted potato cannon, portable engine, adjustable burner, rope winch, propeller bearings.

**Supplementaries:** crank, bellows, gold door, iron gate, globes, sconce lever, item shelf, pedestal, blackboard, safe, lunch basket, presents, cage, notice board, hourglass, faucet, speaker block, turn table, book piles, jars, sack, flower box and planter, cannon, pulley block, lock block, doormat.

Some of these get a full working cycle — the cranks, the valve, the wheel, the bellows; most get a hand that goes to the right place and presses, puts or takes.

</details>

## Supported mods

Everything below is optional — the matching animations turn on when the mod is there.

| Mod | What it covers |
|---|---|
| **[Create](https://modrinth.com/mod/create)** | Hand crank and valve handle turned with the whole body; the weighted ejector; depots, basins, controls and other blocks used by hand |
| **[Create Aeronautics](https://modrinth.com/mod/create-aeronautics)** | Steering wheel, throttle, typewriter, navigation table, rope; the seated cockpit |
| **[Supplementaries](https://modrinth.com/mod/supplementaries)** | Crank, bellows, faucet, globe, jars, shelves and other blocks used by hand |
| **[Curios](https://modrinth.com/mod/curios)** | Putting on accessories |

## Loaders

- **NeoForge 1.21.1**

enjoy ^_^
