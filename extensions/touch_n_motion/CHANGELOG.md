# Touch'n Motion — Changelog

## 1.0.5

### Added

- Create: Lights & Controls: its buttons, levers, lever panel, gear switch and card reader - the hand waits at the control you aim at and taps it when you use it
- Create: Connected's crank wheel and large crank wheel are turned like Create's hand crank: the hand on the handle, going round with it
- Levers with a handle: Create's analog lever (and Create: Connected's linked one), Design n' Decor's stepped lever and breaker switch, Compact Gearbox's controller. The hand goes to the handle when you aim at it and follows it as it moves; with Lever drugster it stays on the handle while you drag it
- At a seat with a wheel these levers work like the throttle: one hand on the wheel, the other on the lever
- Create: Connected's linked throttle lever is held and dragged like Aeronautics' own
- Create trains: a driver seated on the train sits turned to the controls whichever way the camera looks, the head following the camera as far as a neck turns, and sways back speeding up, forwards braking and out of a bend. On the move the hands no longer slip off the levers for a frame. The right hand keeps the right lever and the left the left one, whichever way the driver looks,, each hand reaching its own lever as at a craft's wheel: the upper body leans and turns just enough for both. "Show grip points" marks the two levers too
- Left-handed players use the left hand: buttons, levers and blocks, a hand crank too; the pocket gesture goes to the left hip; at an open chest the right hand holds the lid. A button, a lever or a block out to the side of the other hand is taken with the hand on its side, so an arm no longer reaches across the chest, and the swing of the click goes to that hand, the torso turning with it. What an item goes into (a shelf, a jukebox, a cauldron) stays the main hand's while it holds one; a wheel's and a train's levers keep their sides. New setting "Nearest hand" (under "Press buttons")
- Tables (cartography table, enchanting table, Simulated's navigation table): looked at, the hands go to the top; with the table's screen open and stood right at it, the player leans on it as on a fence - one foot set back by a step, the hips back, the torso over the table - and steps back when the screen closes
- Carry On and TACZ: while you carry something or hold a gun, the arms are left to them - no reaching, gestures, turning in narrow gaps or leaning on walls, and half the lean with the motion; while aiming the torso and the head are left to TACZ too. The feet keep standing on the ground. Settings: "Carry On Compat", "TACZ Compat"
- New setting "Legs along other mounts" (off by default): on a mount that is not a horse - a Pokémon, an Ice and Fire hippogryph, a mod's animal - the legs lie along its sides by its size (Citadel models are measured; others by their hitbox)
- Debug: "Show grip points" puts a spark on each point the hand takes hold of on the levers around you
- Resource packs can read what this mod is doing as EMF variables: emfc_tnm_reach, emfc_tnm_hold, emfc_tnm_lean, emfc_tnm_gesture, emfc_tnm_work, emfc_tnm_ride, how much of each arm, the head and the torso it holds (emfc_tnm_right_arm, emfc_tnm_left_arm, emfc_tnm_head, emfc_tnm_body), walking on a fence or a wall (emfc_tnm_catwalk), and the torso lean it adds (emfc_tnm_lean_x, emfc_tnm_lean_z)

### Changed

- Sable: rope and rail grips share the retained hand-contact pass, following their current rendered anchor through a turn and while letting go; armour copies no longer change the contact measurements
- Horse riding pose, remade on what the boat and the minecart do: the hands hold the reins over the withers, each on its own side of the neck, and stay with the horse as it turns, going after its head as it grazes or rears; the legs grip its sides; the head looks where you look. The pose now goes over the resource pack's instead of replacing it. A hand you use lets go of the reins - eating, drinking, a bow, a crossbow loading or loaded, a shield, a spyglass, a swing - while the other keeps hold; another mod's pose of one arm (Not Enough Animations eating) takes only that arm. New setting "Jockey lean" (under "Riding animation"): the rider leans over the neck, sitting up while a hand is off the reins
- Buttons: the palm pushes the button in with the click's swing
- Putting a helmet on: the player braces for it - a short step out as the hand gets to the helmet, held to the end
- Riding: the legs lie along the mount's sides by how wide it is, measured from its model - a vanilla horse, a donkey, a mule, every breed of Icy's Better Horses - instead of hanging half inside a broad horse or splayed off a narrow pony; the hands take the reins further on a larger horse
- Horse foot IK stands each mount on its own hooves, measured from its model (Icy's Better Horses' draft breeds and ponies too); a mount whose hooves cannot be found is left as it is
- Settings say what they do: "Horse sync" is now "Ride with the horse", "Brace on moving transport" is "Brace on moving sub-levels" (it works on Sable sub-levels); the throttle effort and fright texts are reworded
- Sable: standing on a moving sub-level no longer costs frames - the ground under the feet was scanned again every frame because the world position kept changing, now it is rescanned when the player moves or turns on the sub-level, or it tilts
- Sable: standing or walking on a craft costs less than half the looks around it did - the world is not searched where it is all air around a craft up in the sky, and the deck under a planted foot and the walls to the sides are looked for again only when the player moves or turns on the deck
- Sable: bracing is a reaction to the sub-level changing speed or course: a hand takes hold when it speeds up, slows down, turns or bobs, and lets go gently once it has kept its speed and course for three and a half seconds; Sable's odd one-tick jumps of the deck no longer count. New setting "Brace only while it speeds up or turns" (on); off, a hand holds the whole time the sub-level moves, as before

### Fixed

- Multiplayer: menu work shares its actual put, take or tap and contact point, including crafter slot toggles, instead of a generic put; it is captured in first person too

- Multiplayer: cockpit anchors, both typing hands, actual block breaking, the lead hand and cannon aiming are shared even when the local model is hidden; train control changes send immediately and input is cleared between worlds

- Multiplayer: bursts of hand input keep the newest state, including release, without dropping one-shot gestures; a gesture follows the state it belongs to

- Rope and rail grips ease in from the resource pack pose with one ownership blend; the final contact correction no longer applies that weight a second time

- Sable: door and button searches use the player's place on the deck; reading a sign follows its board in the sub-level, and visibility checks keep world and plot hits apart
- Steering wheels and other two-handed blocks: an unreachable second grip no longer drops the reachable main hand; each hand retains its own reach margin, and an actively held control stays selected while the eyes wander
- A train driver's body stays turned to the controls through the bends, as a boat keeps its rider's: the torso no longer twists after the camera. In the cab the cape hangs down the back instead of flying out with the train's speed (1.20.1 and 1.21.1)
- Shoulder Surfing: driving a train, the camera turns freely and the driver stays turned to the controls, as at a craft's wheel
- On a horse the rider sits in the middle of the saddle, leaning over to the reins, and rides it: carried up, down and turned with the horse's body as the resource pack moves it - over a jump the rider no longer stays upright while the horse rears up under them, nor slides back off it, and with Fresh Animations no longer sits forward on the withers - folding over the neck and giving the horse its head over a jump, swaying back as it sets off, forward as it stops and out of a bend, and coming up off the saddle in the air
- Shoulder Surfing: on a horse, in a minecart and leaning on a fence the head follows the camera as at a craft's wheel and a train's controls; leaning on a fence it no longer steps between ticks
- WATUT: its screen pose steps aside whenever this mod animates the arms (needs EMF Compat: WATUT 1.1.0)
- Create's item drain: the fluid in it is read for subclasses of the drain too
- Aeronautics cockpit: turning sharply to a side lever no longer starts the far hand for a frame before the near one takes it, and the near hand starts a little sooner
- Icy's Better Horses: its breeds' riders are carried by Icy's alone, never moved twice
- Sable: the legs no longer jerk and sink into the body on a moving, bobbing sub-level - standing on one was taken for walking at its speed
- Sable: the game no longer freezes going up stairs on a moving sub-level - a look for a wall to put a hand on could walk a broken ray block by block without end (#63)
- Sable: a look at the ground, a wall or a step in the world no longer takes a sub-level's block for a world point millions of blocks out - feet, hands on walls, squeezing past a wall and a horse's hooves next to a craft got that point now and then, and on a craft flying low the feet got it every frame
- Sable: on a moving sub-level buttons, levers, the throttle, the wheel and other blocks work again, and the hand goes to a wall or a fence - standing on a flying craft was taken for walking at its speed, which turned all of them off; the body's sway follows the player's own moves on the deck, not the craft's
- Sable: walking on a sub-level that sinks, rises or tilts, a planted foot stays on its plank - its height was kept in the world, and the deck ran off from under it, the hip jumping up or down at each step
- Sable: walking down a heeled or rocking sub-level, the resource pack no longer plays its landing again and again - the deck sinks away under each step and the player drops off it for a tick; such a drop now counts as standing on the deck (new setting "Steady footing on sub-levels", on by default). A jump on a moving craft no longer runs the legs in the air: the walk is counted across the deck, not at the craft's speed
- Sable: a hand bracing on a moving sub-level - on a rope, a rail, a wall - is chosen as at buttons and levers: the main hand, or with "Nearest hand" the one on the support's side, so the arm no longer reaches across the chest for a rope beside the other shoulder; left-handed players take it with the left. A rope straight ahead or behind is left alone: only one beside the body is taken
- Better Combat: foot IK leaves the legs to an attack that poses them ("Attack legs") - the mace's step no longer has its foot planted and the hip dragged up after it
- Horse foot IK works only while a player rides the horse: a riderless horse, or one a mob rides, is left as it is - a skeleton trap's horseman no longer floats over a horse sunk into a slope
- Riding a horse with a resource pack that animates it, the player in the inventory's portrait is no longer moved and turned with the saddle
- 1.20.1 and 1.21.1: where the body is drawn turned from the way the game turns it (a craft's wheel, a minecart, looking at someone), the cape swings as from the body drawn, not out to the side
- 1.20.1 and 1.21.1: a resource pack's own cape (Fresh Animations) and an elytra go with the torso wherever it is leant or turned here - riding a horse at speed the cape no longer stands off the back high behind the head
- A feature that fails no longer takes the others' poses with it for that frame; a cape no longer keeps a lean the torso has left
- Pocket gesture: switching to first person while it plays no longer stops it for good - the next pick-up starts it again
- First Person Model with shaders: the helmet no longer plays its putting-on again every two seconds - what is worn is not read while First Person Model hides it from the game
- Better Combat: pose inertia leaves the parts another mod's pose holds alone - a swing's arm is no longer late to its weapon; through the lean an attack's arms keep pointing where the swing has them; a two-handed weapon's stance lends an arm to a button, a lever, a block or a gesture, and gets it back after

### Dependencies

- Requires EMF Compat Core 2.4.0

## 1.0.4

### Added

- With "Log decisions" on, a chat message says when EMF is not animating the player's model, and why: the model switched off in EMF's settings (Smooth Player Animations does this while its CEM Animation Engine is on), no player pack, or animations dropped after an error

### Fixed

- Buttons: the hand reaches for a button only while you aim at it or at the block it is on, not whenever one is near where you look
- Horse riding: an error while posing the horse no longer makes EMF stop animating it for the rest of the session

## 1.0.3

### Fixed

- 3D Armor and other packs with armour models of their own: the armour no longer slides off the body in a pose - it was posed twice
- The head no longer comes off the neck for a few frames at the start of a jump; it showed more the lower the frame rate
- 1.20.1 and 1.21.1: a cape no longer tilts and shifts the wrong way when the torso leans to the side, as on a step
- Shoulder Surfing: steering a moving Sable craft from its seat, the camera no longer drifts to the left or right, and the player no longer turns after the camera
- First Person Model: the camera stays at the head while a gesture leans or shifts the torso
- Aeronautics cockpit: a hand on a wheel or a throttle keeps hold of it while the craft tilts, instead of letting go or switching hands

## 1.0.2

### Fixed

- A cape and an elytra stay on the back while the torso leans or shifts in a gesture, instead of hanging where the resource pack's torso would be
- A player standing by a chest no longer reaches into it when you are the one who opened it; two players with the same chest open both go through it

## 1.0.1

### Fixed

- Fixed the player's pose breaking under shaders (Iris, Oculus): feet sliding apart while walking, hands missing what they reach for, gestures not playing
- 1.21.11 and newer: hands brush past crops and other plants again
- 1.21.11 and newer: milking a mooshroom plays the milking gesture again
- Enhanced Tooltips: hovering a piece of armour no longer makes the player put it on over and over
- Freecam: the player no longer turns their head to look at the free camera

## 1.0.0

### Added

- First release

### Dependencies

- Requires EMF Compat Core 2.3.0
