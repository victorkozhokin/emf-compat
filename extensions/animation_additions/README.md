# EMF Compat: Animation Additions

> [!IMPORTANT]
> **Experimental:** version 0.1.0, not published yet. Things here are tried out, looked at in game and may still change or go.

A mod, client-side with an optional server part, that adds animations of its own on top of an **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player model: feet that stand on the ground they are on, hands that go to the thing you use, and a body that takes part in what the hands do.

Made for and tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**. The pack keeps its own walk, idle motion and expressions; this addon only adds to them. Other player animation packs may give different results.

## Features

**Movement and body**

- Feet follow stairs, slabs and other uneven ground instead of floating over it or sinking in.
- A careful stance on fences, walls and other narrow supports.
- The torso leans with what the hands do and with your own motion; poses carry a little inertia.
- In a gap too narrow to walk through square, the body turns to fit it.

**Hands and surroundings**

- A hand rests on a wall you stand beside, and brushes through grass, crops and flowers.
- A glance at a creature nearby while standing still.
- Leading an animal, the hand follows the lead and the body leans against the pull.
- After picking things up, one gesture puts them away in a back pocket — not one per item.

**Blocks and controls**

- Buttons, levers, doors and gates are pressed, pulled and held at the place where they are.
- Lecterns, chests, bookshelves, jukeboxes, flower pots, candles, bells, workstations and more are used by hand.
- Mining swings the tool on to the block you break: a stance for each tool, three different blows, the body going with them.

**Gestures and care**

- Feeding an animal: the food to its mouth, then the other hand strokes it. Milking and shearing are done bent to the animal.
- The hand is already held out before you click, so the gesture does not lag behind the action.
- Putting a piece on an armour stand, and pressing a seed into the bed.
- Looking through an open chest, barrel or shulker box, the hands working when something is moved.
- Putting on armour: a helmet pushed down with one hand, a chestplate settled with both, leggings and boots looked over. Curios accessories go to where they are worn.
- Shaking off water, powder snow and mud.
- These show on other players too.

**Riding and transport**

- You rise and fall with an animated horse instead of sinking into the saddle, with an optional riding pose.
- On moving transport the hands brace against what is near.

## Everything it does

| | Feature | What you see |
|---|---|---|
| **Movement** | Foot IK | Feet stand on stairs, slabs and uneven ground; the step up is prepared ahead. |
|  | Balance | A careful stance on fences, walls, bars and slopes. |
|  | Torso lean and inertia | The torso goes with your motion and with the hands; poses settle with weight. |
|  | Narrow passages | The body turns to fit the gap. |
| **Surroundings** | Hand on the wall | A hand rests on the wall beside you. |
|  | Plants | Hands brush through grass, crops and flowers. |
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
|  | Other players | All of these show on other players too. |
| **Machines** | Cranks and valves | Turned with the whole body: feet, hips, torso and hands. |
|  | Steering wheel | Held hand over hand, one hand always on the rim. |
|  | Cockpit | Seated: the wheel in one hand, a throttle or typewriter in the other. |
|  | Weighted ejector | Bracing on the lid, the launch, the flight. |
| **Transport** | Moving transport | Hands brace on what is near; a rope held at the edge. |
|  | Horses | You stay on the saddle of an animated horse; optional riding pose. |
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
| **[Create](https://modrinth.com/mod/create)** | Hand crank and valve handle turned with the whole body; depots, basins, drains, funnels and other blocks used by hand; the weighted ejector's launch. |
| **[Create Aeronautics](https://modrinth.com/mod/create-aeronautics)** | Steering wheel with hand-over-hand grips, throttle, linked typewriter, navigation table, rope winch; a seated cockpit with the wheel in one hand and a side control in the other. |
| **[Supplementaries](https://modrinth.com/mod/supplementaries)** | Crank, bellows, faucet, globe, jars, pedestals, shelves and other blocks used by hand. |
| **[Curios](https://modrinth.com/mod/curios)** | Putting on accessories: ring and bracelet, necklace, belt, back and head pieces. |
| **[ParCool](https://modrinth.com/mod/parcool)** | Nothing of its own: the addon stands down in ParCool's hanging, climbing, vaulting, rolling and diving moves, and goes on working in a fast run. |

Contacts follow a moving Sable sub-level, so they hold on a ship under way. Interactions on Create contraptions are not covered, apart from driving controls.

## Multiplayer

The addon works on the client alone, on any server: what other players do is then guessed from what the game shows of them — where they look, the swing of an arm, the block that changed.

Put the same jar on a **NeoForge server** as well and nothing is guessed. Each player's game tells the server what its hands are at — the buttons held, the block or the creature under the crosshair (a ship's blocks included), the control held on to, the key typed, the click the game accepted — and the server passes it on to the players nearby. Turning a crank, holding a wheel or a throttle, typing, mining, feeding an animal then look to others as they do to you.

The server does nothing else with it: no world or gameplay change, nothing stored. Players without the addon can join such a server, and players with it can join servers without it.

## Config

Open the in-game config screen (Mods → EMF Compat Core → Config) and pick the **Animation Additions** tab. The button at the top turns the whole addon off and keeps the settings.

**Movement & body**

| Option | What it does |
|---|---|
| Foot IK (experimental) | Feet stand on the ground under them. Under it: **Balance on narrow supports and slopes**, **Foot IK for horses**. |
| Torso lean | The torso bends and turns with what the hands do. Under it: **Lean with the motion**. |
| Pose inertia | Poses settle with a little weight instead of stopping dead. |
| Give way to ParCool moves | In the ParCool moves that animate the whole body their own way — hanging, climbing, vaulting, rolling, diving and the like — this addon adds nothing. A fast run keeps the foot IK and the lean. |

**Hands & surroundings**

| Option | What it does |
|---|---|
| Hand on the wall | A hand rests on a wall beside you. |
| Keep out of walls | The body turns to fit a narrow passage. |
| Hands brush plants | Hands go through the plants you walk in. |
| Look at things nearby | Standing idle, the head turns to a creature near you and eases back when you do anything. |
| ↳ Idle time before looking | How long you stand still first: 1–30 s, 5 by default. |
| ↳ Players / Villagers and traders / Animals / Monsters | Which creatures are looked at. Monsters are off by default. |
| ↳ Read signs | Looking at a sign close by, the head settles on it and the body leans in. |
| ↳ Turn the body in steps | A creature too far round for the neck is turned to with the body, the feet stepping round. |
| Hold animal leads | The hand and the body follow the lead. |
| Pocket what was picked up | One gesture after a run of pickups. |

**Blocks & controls**

| Option | What it does |
|---|---|
| Press buttons | Buttons and levers pressed where they are. Under it: **Throttle effort**. |
| Reaching pose | The body stretches to a control out of arm's reach. |
| Use blocks by hand | The hand goes to the block being used. |
| Hold doors | A hand on the door or gate as it is opened. |
| Lecterns and chests | Pages turned, lids opened. |
| Mining | The tool is swung on to the block. |
| Weighted ejector | Bracing on the lid, then the launch and the flight. |

**Gestures & care**

| Option | What it does |
|---|---|
| Feed, milk and shear by hand | Under it: **Feeding** (and **Stroke it after**), **Milking**, **Shearing**. |
| Dress an armour stand by hand | The hand goes to the part of the stand that was clicked. |
| Plant seeds by hand | Bending down and pressing the seed in. |
| Look through open containers | Under it: **Hands answer what is moved**. |
| Put on armour and accessories | Under it: **Helmet**, **Chestplate**, **Leggings and boots**, **Accessories**. |
| Shake off water, snow and mud | Under it: **After water**, **After powder snow**, **After mud**. |
| Hand out before the click | The hand is held out as soon as you aim at the thing with the right item. |
| Other players' gestures | Shows these gestures on other players as well. |
| Lean in until the hand is there | The torso and hips shift so the hand lands on what it reaches for. |
| Step into a stance | The feet step apart for a gesture and back after. |
| Look at what the hands do | The head turns to the thing being worked on. |
| Give a gesture up when leaving | Walking off or turning away brings the hands back at once. |
| The other arm joins in | The free arm waits half raised or goes out against the lean. |
| Act when the hand gets there | Off by default. The game waits for the hand before feeding, milking, shearing, dressing a stand or planting. |

**Riding & transport**

| Option | What it does |
|---|---|
| Horse sync | Keeps you on the saddle of an animated horse. Under it: **Riding animation**. |
| Brace on moving transport | Hands hold on to what is near while the vehicle moves. |

**Debug** holds logging switches; leave them off for normal play.

## Dependencies

- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.3.2+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)
- EMF Compat Core 2.2.0+
- [Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension) (recommended)

## Supported loaders / versions

| Loader | Minecraft versions |
|--------|-------------------|
| NeoForge | 1.21.1 |

## Build

```bash
./gradlew :animation-additions-neoforge-1.21.1:build
```

enjoy ^_^
