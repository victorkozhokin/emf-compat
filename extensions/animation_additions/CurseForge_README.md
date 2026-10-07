# EMF Compat: Animation Additions

A client-side mod that adds animations of its own on top of an **[Entity Model Features](https://www.curseforge.com/minecraft/mc-mods/entity-model-features)** player model: feet that stand on the ground they are on, hands that go to the thing you use, and a body that takes part in what the hands do.

Made for and tested with **[Fresh Animations: Player Extension](https://www.curseforge.com/minecraft/texture-packs/fa-player-extension)**. The pack keeps its own walk and idle motion, and the face keeps the expressions of **[Just Expressions](https://modrinth.com/resourcepack/just-expressions)**, the add-on for it; this addon only adds to them. Other player animation packs may give different results.

**Experimental:** an early version — things may still change.

## Features

- Feet stand on stairs, slabs and uneven ground; a careful stance on fences and other narrow supports.
- Hands go to the thing you use: buttons, levers, doors, chests, workstations and dozens of other blocks.
- The whole body takes part: the torso leans, the feet step into a stance, the weight shifts.
- Mining swings the tool on to the block, with a stance for each tool and three different blows.
- Feeding, milking and shearing by hand; dressing an armour stand; planting seeds; looking through an open chest.
- Putting on armour and Curios accessories; shaking off water, snow and mud; pocketing what was picked up.
- Fishing with the whole body: the cast, the wait, the bite and the haul.
- Fright at a sudden sound - sculk, a warden, a creeper's hiss, an explosion, thunder: a shudder and a step back, the hands on guard.
- Leaning on a fence; a look at a creature nearby, the body stepping round to it; signs are read.
- Cranks, valves, steering wheels, throttles and typewriters of Create, Aeronautics and Supplementaries are worked with both hands — also seated in a cockpit and on a ship under way.
- You stay on the saddle of an animated horse; a boat is rowed with the hands on the oars, a minecart ridden with the hands on its sides.
- Works for other players too.
- Every animation has its own switch in the config.

## Everything it does

| | Feature | What you see |
|---|---|---|
| **Movement** | Foot IK | Feet stand on stairs, slabs and uneven ground; the step up is prepared ahead. |
|  | Balance | A careful stance on fences, walls, bars and slopes. |
|  | Torso lean and inertia | The torso goes with your motion and with the hands; poses settle with weight. |
|  | Narrow passages | The body turns to fit the gap. |
| **Surroundings** | Hand on the wall | A hand rests on the wall beside you. |
|  | Plants | Hands brush through crops as you walk by or through them; grass and flowers as an option. |
|  | Leaning on a fence | Stood up against a fence or a wall, the hands go on its top and the body leans on them. |
|  | Look at things | Standing idle, a look at a creature nearby, the body stepping round if it is far to the side; signs are read. |
|  | Animal leads | The hand follows the lead, the body leans against the pull. |
|  | Pocket | One gesture after a run of pickups puts them away. |
| **Blocks** | Buttons and levers | Pressed at their real height — standing, crouched or seated. |
|  | Reaching | The body stretches for a control past arm's length. |
|  | Doors and gates | A hand on them as they open. |
|  | Blocks used by hand | Lids, pages and work slots: the hand goes to the right place. |
|  | Mining | A stance for each tool, three different blows, the body going with them. |
| **Gestures** | Feeding | The food to the animal's mouth, then a stroke with the other hand. |
|  | Milking and shearing | Done bent to the animal, both hands at work. |
|  | Hand out before the click | Aiming with the right item already holds the hand out. |
|  | Armour stand | The hand goes to the part that was clicked. |
|  | Planting | Seeds pressed into the bed, along a row without straightening up. |
|  | Open containers | One hand on the edge, the other looking through. |
|  | Putting on armour | Helmet, chestplate, leggings and boots — each its own way. |
|  | Curios accessories | Put on where they are worn. |
|  | Shaking off | After water, powder snow and mud. |
|  | Fishing | The cast, the wait, the bite and the haul, each with the whole body. |
|  | Fright | A frightening sound near by: a shudder and a step back from it, a hop back from a worse one, the hands on guard and a look at where it came from. |
|  | Other players | All of these show on other players too. |
| **Machines** | Cranks and valves | Turned with the whole body: feet, hips, torso and hands. |
|  | Steering wheel | Held hand over hand, one hand always on the rim. |
|  | Cockpit | Seated: the wheel in one hand, a throttle or typewriter in the other. |
|  | Weighted ejector | Bracing on the lid, the launch, the flight. |
| **Transport** | Moving transport | Hands brace on what is near; a rope held at the edge. |
|  | Horses | You stay on the saddle of an animated horse; optional riding pose. |
|  | Boats | The hands on the oars and the body in the stroke; a passenger sits in the bow facing the rower; a chest boat keeps its chest in the bow. |
|  | Minecarts | Sat against the back, the hands on the sides, the body thrown about by the bends and the drops. |
|  | Sable sub-levels | Contacts follow a ship under way. |

## Blocks you can interact with

**Vanilla:** buttons, levers, doors, fence gates, chests, barrels, lectern, chiseled bookshelf, jukebox, note block, composter, cauldron, beehive and bee nest, respawn anchor, cake, candles and candle cake, flower pot, repeater, comparator, daylight detector, enchanting table, cartography table, brewing stand, crafting table, stonecutter, crafter, vault, bell, TNT, campfire.

**Create:** hand crank, valve handles, weighted ejector, train controls, contraption controls, factory gauge, content observer, track station, depot, basin, item drain, blaze burner, mechanical arm, deployer, mechanical crafter, package frogport, packager and repackager, postbox, rotation speed controller, sequenced gearshift, stockpile switch, display link, redstone link, elevator contact, stock ticker, redstone requester, funnels, smart chute, creative motor, mechanical bearing.

**Create Aeronautics:** steering wheel, throttle lever, linked typewriter, navigation table, physics assembler, mounted potato cannon, portable engine, adjustable burner, rope winch, propeller bearings.

**Supplementaries:** crank, bellows, gold door, iron gate, globes, sconce lever, item shelf, pedestal, blackboard, safe, lunch basket, presents, cage, notice board, hourglass, faucet, speaker block, turn table, book piles, jars, sack, flower box and planter, cannon, pulley block, lock block, doormat.

Some of these get a full working cycle — the cranks, the valve, the wheel, the bellows; most get a hand that goes to the right place and presses, puts or takes.

## Supported mods

Everything below is optional — the matching animations turn on when the mod is there.

| Mod | What it covers |
|---|---|
| **[Create](https://www.curseforge.com/minecraft/mc-mods/create)** | Hand crank and valve handle turned with the whole body; the weighted ejector; depots, basins, controls and other blocks used by hand |
| **[Create Aeronautics](https://www.curseforge.com/minecraft/mc-mods/create-aeronautics)** | Steering wheel, throttle, typewriter, navigation table, rope; the seated cockpit |
| **[Supplementaries](https://www.curseforge.com/minecraft/mc-mods/supplementaries)** | Crank, bellows, faucet, globe, jars, shelves and other blocks used by hand |
| **[Curios](https://www.curseforge.com/minecraft/mc-mods/curios)** | Putting on accessories |
| **[Enchanted Fishing Line](https://modrinth.com/mod/enchanted-fishing-line)** | The line starts at the rod's tip as the fishing pose has it |

## Multiplayer

Works on the client alone, on any server: what other players do is then guessed from where they look and how they swing.

Put the same jar on a **NeoForge server** too and nothing is guessed — each player's game tells the others what its hands are at, so a crank turned, a wheel held, a key typed or an animal fed looks to others as it does to you. The server only passes this on; it changes nothing in the world. Players without the addon can still join.

## Loaders

- **NeoForge 1.21.1**

enjoy ^_^
