# Touch and Motion

Your character touches the world, shifts their weight and puts their whole body into everyday actions.

**Touch and Motion** adds procedural player animations on top of an **Entity Model Features** resource pack. Feet adapt to uneven ground, hands reach for actual objects, and the torso follows the effort of turning a crank, steering a ship or leading an animal.

Designed around **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** (FA+Player), it keeps the pack's walking, idle motion and expressions as the foundation. Additional poses adapt to the height and position of the target, the player's stance and the space available around them.

The current development build is **EMF Compat: Animation Additions 0.1.0**. Its file name and settings tab still use Animation Additions.

## Movement that responds to the surroundings

- **Feet on uneven ground.** Foot IK adjusts the feet to stairs, slabs and other collision surfaces, with preparation for stepping up.
- **Balance on narrow supports.** Fences, walls and similar surfaces get a more deliberate stance, with arms and torso helping the character balance.
- **Narrow passages and walls.** The body turns to fit the available space; hands can brace against nearby walls, with the legs following the adjustment. Along an ordinary outside wall, the free arm keeps its usual motion.
- **Weight and inertia.** Body lean, small support steps and weight shifts connect hand movements to the rest of the character.
- **Plants and nearby creatures.** Hands brush through vegetation, and the character can glance at a nearby creature while idle.
- **Animated horses.** The rider follows an EMF-animated horse instead of sitting independently of its motion.

## Hands that interact with objects

Buttons, levers, doors, gates, containers and other supported blocks have their own contact points and gestures. A hand can press, pull, place, take or hold, depending on the action.

Examples include chiseled bookshelves, lecterns, jukeboxes, flower pots, candles, bells, composters and workstations. Container searching adds a separate waiting pose and movement during inventory actions. Mining also has a tool-directed swing, with further refinement in progress.

The character adjusts their reach for standing, crouching and supported seated interactions. When an interaction ends or its target is lost, the pose blends back into the resource pack's animation. Hand ownership is shared with the EMF Compat framework so competing interactions can take priority.

## Create and Aeronautics

These integrations are optional: the relevant mods must be installed for their blocks and vehicles to exist.

| Interaction | Animation |
|---|---|
| **Create Hand Crank** | A supported stance, weight transfer and coordinated movement of the legs, pelvis, torso and working hand. |
| **Create Valve Handle** | Two-handed operation with a supporting stance and body movement. |
| **Create Weighted Ejector** | Bracing on the moving lid, followed by launch and flight poses. |
| **Aeronautics Steering Wheel** | Alternating hand changes around six grip positions on the rim, keeping one hand in contact during a regrip. |
| **Aeronautics Throttle** | Reaching and leaning toward the control, including side controls in a seated cockpit. |
| **Aeronautics Navigation Table** | Leaning over the table with hand support and a supporting stance. |
| **Moving transport and ropes** | Bracing against nearby support, counterbalancing vehicle motion and a dedicated rope-holding pose near the edge. |

Supported contacts can follow moving Sable sub-levels in their local space. Coverage depends on the particular block and interaction.

### A cockpit that uses both hands

Sit facing a steering wheel with a throttle or a Supplementaries typewriter beside the seat. The character can lean toward the side control while the other hand stays assigned to the wheel. Typing produces short key-press movements, and returning to steering restores the rim grip.

Left and right layouts are supported. The seated body stays oriented toward the wheel while the head follows the player's view.

## Supplementaries

- **Typewriter:** key-press gestures, including use beside a seated cockpit.
- **Bellows:** two-handed compression with body movement and weight transfer.
- **Other supported blocks:** interactions with items, books, jars, faucets, switches and more, using block-specific contacts where available.

These are visual interactions with existing gameplay. Some blocks use a short tap or placement gesture rather than a complete animated operating cycle.

## Small everyday gestures

- **Leading animals:** the hand follows leash tension, the body counterbalances, and stopping adds a short pulling gesture.
- **Putting pickups away:** after a series of item pickups, a delayed pocket gesture suggests stashing them away without animating every item separately.
- **Animal care:** feeding and petting, milking and shearing, with reaching, body lean and a supporting stance.
- **Planting seeds and equipping an armour stand:** short movements directed toward the interaction point.
- **Putting on armour:** gestures for the helmet and chestplate; boots and leggings trigger an inspection of the left leg, turning it inward and outward with a lowered gaze.
- **Curios accessories:** equipment gestures for recognised slot locations, such as the hand, neck or waist. Native in-game coverage is still being validated.
- **Shaking off water, powder snow and mud:** a short body shake after leaving the environment.

## Installation

The current implementation targets **Minecraft 1.21.1 on NeoForge** and runs on the client. A server installation is not required for these visual additions.

1. Install **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** **3.3.2 or newer** and its required **[Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures)** dependency, using builds for your Minecraft version and loader.
2. Install **[EMF Compat Core](../../core/README.md)** **2.2.0 or newer**.
3. Put the Animation Additions jar in your client's `mods` folder.
4. Enable **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and the dependencies required by that resource pack.
5. Install Create, Aeronautics, Supplementaries or Curios if you want their corresponding interactions.

Other player animation packs may produce different results. Contact positions and the style of the added movements are developed and checked primarily with FA+Player.

## Settings

Open **Mods → EMF Compat Core → Config → Animation Additions**.

Settings are grouped into **Movement & body**, **Hands & surroundings**, **Blocks & controls**, **Gestures & care**, and **Riding & transport**. You can disable individual features or use the addon's master switch while keeping their settings. The Core's global compatibility switch also covers this addon.

## How the animations work

The resource pack animates the player first. Touch and Motion then adds authored movement phases and uses inverse kinematics to direct limbs toward contacts in the world. Support steps, pelvis movement and torso adjustments make the action visible throughout the body.

The additional animations are generated by code; they do not directly play animation files from Fresh Animations PE or Actions & Stuff. FA+Player provides the base pose and visual style, while other animation references inform timing and movement. The existing straight-limb rig is preserved, without adding knee joints or finger animations.

## Development status

This is an experimental **0.1.0** build. Mining, some early block interactions, extreme reach cases and parts of the moving-transport support still need refinement. Arbitrary custom player models, accessory slots and multiplayer scenarios are not all covered by the current game tests. Some gestures use local interaction information that is unavailable for remote players.

For the current work queue, see the [animation to-do](../../docs/animation-todo.md). The [feature catalogue](../../docs/showreel-feature-catalog.md) lists supported interactions and their demonstration status.

## Build from source

Use JDK 21 from the repository root:

```bash
./gradlew :animation-additions-neoforge-1.21.1:build --configure-on-demand
```

The jar is copied to `upload/Animation Additions/neoforge/1.21.1/`.

## Credits and licence

Developed by **STRadaT** as part of EMF Compat. Player animation foundation: **Fresh Animations: Player Extension**. **Actions & Stuff** is a movement reference; its assets are not bundled with this addon.

Mod licence: **GNU GPL 3.0**. Referenced mods and resource packs retain their own licences.
