# Touch'n Motion — Changelog

## 1.0.5

- Create: Lights & Controls: its buttons, levers, lever panel, gear switch and card reader - the hand waits at the control you aim at and taps it when you use it
- Create: Connected's crank wheel and large crank wheel are turned like Create's hand crank: the hand on the handle, going round with it
- Levers with a handle: Create's analog lever (and Create: Connected's linked one), Design n' Decor's stepped lever and breaker switch, Compact Gearbox's controller. The hand goes to the handle when you aim at it and follows it as it moves; with Lever drugster it stays on the handle while you drag it
- At a seat with a wheel these levers work like the throttle: one hand on the wheel, the other on the lever
- Debug: "Show grip points" puts a spark on each point the hand takes hold of on the levers around you
- Create: Connected's linked throttle lever is held and dragged like Aeronautics' own
- Create trains: a driver seated on the train sits turned to the controls whichever way the camera looks, the head following the camera as far as a neck turns, and sways back speeding up, forwards braking and out of a bend. On the move the hands no longer slip off the levers for a frame. The right hand keeps the right lever and the left the left one, whichever way the driver looks,, each hand reaching its own lever as at a craft's wheel: the upper body leans and turns just enough for both. "Show grip points" marks the two levers too
- A train driver's body stays turned to the controls through the bends, as a boat keeps its rider's: the torso no longer twists after the camera. In the cab the cape hangs down the back instead of flying out with the train's speed (1.20.1 and 1.21.1)
- Shoulder Surfing: driving a train, the camera turns freely and the driver stays turned to the controls, as at a craft's wheel
- 1.20.1 and 1.21.1: where the body is drawn turned from the way the game turns it (a craft's wheel, a minecart, looking at someone), the cape swings as from the body drawn, not out to the side
- Horse riding pose, remade on what the boat and the minecart do: the hands hold the reins over the withers, each on its own side of the neck, and stay with the horse as it turns, going after its head as it grazes or rears; the legs grip its sides; the head looks where you look. The pose now goes over the resource pack's instead of replacing it. A hand you use lets go of the reins - eating, drinking, a bow, a crossbow loading or loaded, a shield, a spyglass, a swing - while the other keeps hold; another mod's pose of one arm (Not Enough Animations eating) takes only that arm. New setting "Jockey lean" (under "Riding animation"): the rider leans over the neck, sitting up while a hand is off the reins
- On a horse the rider sits in the middle of the saddle, leaning over to the reins, and rides it: carried up, down and turned with the horse's body as the resource pack moves it - over a jump the rider no longer stays upright while the horse rears up under them, nor slides back off it, and with Fresh Animations no longer sits forward on the withers - folding over the neck and giving the horse its head over a jump, swaying back as it sets off, forward as it stops and out of a bend, and coming up off the saddle in the air
- Shoulder Surfing: on a horse, in a minecart and leaning on a fence the head follows the camera as at a craft's wheel and a train's controls; leaning on a fence it no longer steps between ticks
- Riding a horse with a resource pack that animates it, the player in the inventory's portrait is no longer moved and turned with the saddle
- Left-handed players use the left hand: buttons, levers and blocks, a hand crank too; the pocket gesture goes to the left hip; at an open chest the right hand holds the lid. A button, a lever or a block out to the side of the other hand is taken with the hand on its side, so an arm no longer reaches across the chest, and the swing of the click goes to that hand, the torso turning with it. What an item goes into (a shelf, a jukebox, a cauldron) stays the main hand's while it holds one; a wheel's and a train's levers keep their sides. New setting "Nearest hand" (under "Press buttons")
- 1.20.1 and 1.21.1: a resource pack's own cape (Fresh Animations) and an elytra go with the torso wherever it is leant or turned here - riding a horse at speed the cape no longer stands off the back high behind the head
- Create's item drain: the fluid in it is read for subclasses of the drain too
- Aeronautics cockpit: turning sharply to a side lever no longer starts the far hand for a frame before the near one takes it, and the near hand starts a little sooner
- Requires EMF Compat Core 2.4.0
- Carry On and TACZ: while you carry something or hold a gun, the arms are left to them - no reaching, gestures, turning in narrow gaps or leaning on walls, and half the lean with the motion; while aiming the torso and the head are left to TACZ too. The feet keep standing on the ground. Settings: "Carry On Compat", "TACZ Compat"
- Resource packs can read what this mod is doing as EMF variables: emfc_tnm_reach, emfc_tnm_hold, emfc_tnm_lean, emfc_tnm_gesture, emfc_tnm_work, emfc_tnm_ride, how much of each arm, the head and the torso it holds (emfc_tnm_right_arm, emfc_tnm_left_arm, emfc_tnm_head, emfc_tnm_body), walking on a fence or a wall (emfc_tnm_catwalk), and the torso lean it adds (emfc_tnm_lean_x, emfc_tnm_lean_z)

## 1.0.4

- Buttons: the hand reaches for a button only while you aim at it or at the block it is on, not whenever one is near where you look
- Horse riding: an error while posing the horse no longer makes EMF stop animating it for the rest of the session
- With "Log decisions" on, a chat message says when EMF is not animating the player's model, and why: the model switched off in EMF's settings (Smooth Player Animations does this while its CEM Animation Engine is on), no player pack, or animations dropped after an error

## 1.0.3

- 3D Armor and other packs with armour models of their own: the armour no longer slides off the body in a pose - it was posed twice
- The head no longer comes off the neck for a few frames at the start of a jump; it showed more the lower the frame rate
- 1.20.1 and 1.21.1: a cape no longer tilts and shifts the wrong way when the torso leans to the side, as on a step
- Shoulder Surfing: steering a moving Sable craft from its seat, the camera no longer drifts to the left or right, and the player no longer turns after the camera
- First Person Model: the camera stays at the head while a gesture leans or shifts the torso
- Aeronautics cockpit: a hand on a wheel or a throttle keeps hold of it while the craft tilts, instead of letting go or switching hands

## 1.0.2

- A cape and an elytra stay on the back while the torso leans or shifts in a gesture, instead of hanging where the resource pack's torso would be
- A player standing by a chest no longer reaches into it when you are the one who opened it; two players with the same chest open both go through it

## 1.0.1

- Fixed the player's pose breaking under shaders (Iris, Oculus): feet sliding apart while walking, hands missing what they reach for, gestures not playing
- 1.21.11 and newer: hands brush past crops and other plants again
- 1.21.11 and newer: milking a mooshroom plays the milking gesture again
- Enhanced Tooltips: hovering a piece of armour no longer makes the player put it on over and over
- Freecam: the player no longer turns their head to look at the free camera

## 1.0.0

- First release
- Requires EMF Compat Core 2.3.0
