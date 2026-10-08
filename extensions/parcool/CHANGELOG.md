# EMF Compat: ParCool — Changelog

## 1.0.0

- First release
- Vaults, wall runs, rolls, climbing, hanging and every other parkour move stay visible instead of being overwritten by your resource pack, for you and for everyone else on the server
- Works with both ParCool 3.4.x and ParCool 4, picking the right path by itself. ParCool 3.4 support is basic: the pose is held by rotation only, so limbs stay attached to your pack's body, without ParCool 4's blending and extras
- On ParCool 4 only the limbs an action actually moves are held, so the rest of your pack's animation keeps playing
- On ParCool 4 moves ease in and out of your pack's animation at ParCool's own pace instead of snapping — vaults and other moves that start at full weight included, so a vault out of a pack-animated fast run no longer jumps to ParCool's run pose first
- While your hands are full with a Carry On block or mob or a Barehanded structure, the moves that need hands are off: grabbing a ledge or a bar, climbing up, vaults, dodges, flips, crawling and the landing roll
- Attack with Better Combat, play an Immersive Melodies instrument or eat while fast running, crawling or charging a jump: the arms do the action, the legs keep running
- Resource packs can animate the moves themselves through new EMF variables — fast run, charge jump, hanging, the bar swing, and the arm and leg angles the addon works out
- A Fresh Animations: Player Extension module ships inside the jar, with FreshLX's permission: turn on "EMF Compat: ParCool Animations" in the resource pack list, above FA+Player
- The module hangs you off a ledge properly: the hands rest on top of the block, the far hand hangs loose when ParCool lets go of it, catching a ledge swings the body under the hands, shimmying goes hand over hand, and round a corner the body turns while the hands reach onto the next face
- Hanging under a bar with the module, the hands hold the bar wider than the shoulders with the body behind it; along the bar the free arm swings round past the body to the next grip while the body turns and swings after it, as monkeys go, and sideways the hands go hand over hand. Moving along a bar is a little slower while the module animates it, so the swing can be seen
- The module's head looks where you look while you hang, as far as a neck turns, instead of staring at the wall
- With the module, crawling and fast swimming show Fresh Animations' own crawl and swim, and climbing a chain or pole shows its ladder climb
- The module keeps Fresh Animations' cape on the back while ParCool poses the torso, such as climbing up a ledge
- A config tab with a master switch and a switch for holding the head and torso too
- Requires EMF Compat Core 2.3.0 and Entity Model Features 3.3.2+