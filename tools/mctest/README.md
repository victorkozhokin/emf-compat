# mctest — running and driving the game myself

For new animation tests, extend the existing **EMF ATLAS - Animation Campus** with an isolated
section instead of placing another detached rig. Use a sandbox copy for verification, keep GUI
hidden, and preserve the original world's other sections when exporting changes. Zone 10 is the
balance annex: `polygon/terrain_section.py` writes only `emf_atlas:terrain`; it does not rebuild
the campus. `scenarios/atlas-balance-regression.json` records idle/crouched/crosswise stance and
sprint on fence, stone wall and a full floor, including model transforms and ground states.
Save its results as `build/balance-regression-after.json`, then run
`python3 tools/mctest/balance_regression_report.py` for assertions and a GUI-free frame gallery.
Use the Test profile's enabled FA+Player packs at startup; reloading fonts in front of the
Atlas text displays can crash Minecraft's glyph upload independently of the animation hooks.
`scenarios/atlas-terrain-balance.json` tests standing/crouching contact and transitions
on its fence, wall, iron bars and slopes. Navigation: the **10 / BALANCE LAB** button at the hub.

Zone 11 / **WALL CONTACT LAB** extends the eastern annex with a narrow passage, a normal-width
passage and broken walls. Generate only that section with `polygon/wallhand_section.py`, then
`/reload` and `/function emf_atlas:wallhand` in the sandbox. The zone 10 button leads to it;
direct entry is `/tp @s 2317 151 2107 180 0`.
Run `scenarios/atlas-wallhand.json`, save the driver result as `build/wall-after.json`, then run
`python3 tools/mctest/wallhand_report.py`. It asserts stable turn direction, settled palm contact,
exit release and hand ownership. Teleports are followed by `look` to explicitly set body yaw:
vanilla teleport alone can leave the old torso heading even after the head turns.
The optional third argument `wallhand` to `polygon/publish_terrain_section.py` publishes only
this section's chunks/function/tagged labels, with the same backup and entity-preservation rules.

Launches any Modrinth profile in a throwaway sandbox, with this repo's freshly built jars in place
of the installed ones, and drives it from inside: commands, key presses, camera, screenshots, and
probes into the core. Written so animation work can be checked in game without asking the user to
look at anything.

The profile is never written to. Everything happens in `run/mctest/<profile>/` (gitignored).

## Parts

| Part | What it is |
|---|---|
| `mctest.py` | The launcher: reads Modrinth's data, builds the sandbox, starts the game, talks to the driver. Also a CLI. |
| `server.py` | MCP server over `mctest.py` — the tools `mc_*` I call. Crops screenshots and lays them out on contact sheets. |
| `driver/<loader>-<mc>/` | The in-game mod, one module per target. A Gradle build of its own. |
| `scenarios/*.json` | Ready-made scripts — a step list is exactly what `steps` takes. |
| `.mcp.json` (repo root) | Registers the server for the session. |

## Using it

```
mc_profiles()                                  # what exists, and whether a driver is built for it
mc_launch("Test", world="New World2")          # sandbox + offline launch, waits for the world
mc_steps("Test", [...])                        # run a script, get results + screenshots
mc_log("Test", 80, "error")  /  mc_status(...)
mc_stop("Test")
```

Without the MCP tools loaded, the same thing from the shell:

```bash
python3 tools/mctest/mctest.py profiles
python3 tools/mctest/mctest.py launch Test --world "New World2" --fresh-world
python3 tools/mctest/mctest.py wait Test
python3 tools/mctest/mctest.py steps Test '[{"camera":"front"},{"wait":10},{"screenshot":"a"}]'
python3 tools/mctest/mctest.py stop Test
```

A saved scenario is just a step list, so it runs straight from the shell:

```bash
python3 tools/mctest/mctest.py steps Test "$(cat tools/mctest/scenarios/worn-items-scene.json)"
python3 tools/mctest/mctest.py steps Test "$(cat tools/mctest/scenarios/packs-fa.json)"   # reload
python3 tools/mctest/mctest.py steps Test "$(cat tools/mctest/scenarios/worn-items-shots.json)"
```

| Scenario | What it sets up |
|---|---|
| `worn-items-scene.json` | Create seats + stock tickers with a villager, a zombie and a parrot sitting on them (they get Create's logistics hat), a villager in a carved pumpkin as the vanilla reference, and Artifacts on the player. Needs `enable=["artifacts"]` at launch. |
| | The parrot is the telling one: its head box is 2×2, so the scale Create takes from it is 0.25 and a hat that misses it comes out four times too big. A parrot never *falls onto* a seat, so the scene seats a chicken first (that is what spawns Create's seat entity), mounts the parrot onto it with `/ride`, and kills the chicken. |
| `worn-items-scene-1.21.11.json` | The same without curios/Artifacts, with 1.21.5+ `equipment:` NBT. |
| `worn-items-shots.json` | Camera positions, screenshots and the `model` probe for that scene. |
| `instruments-scene.json` | A zombie, skeleton, pillager, vindicator, evoker and piglin playing Immersive Melodies instruments, under a lit roof so the undead do not burn. |
| `instruments-shots.json` | Camera positions and screenshots for that scene. Toggle `immersivemelodies.mobs` to compare. |
| `worn-items-scene-1.20.1.json` / `-26.json` | The worn-items scene for Forge 1.20.1 (`Count`/`tag` NBT, no curios) and for 26.x (renamed gamerules, a pause for the chunks before the fill). |
| `chain-conveyor-scene.json` / `-26.json` | Two Create chain conveyors joined by a chain, the player under it with a wrench. Connections are written again once both blocks exist — Create drops a connection to a block that is not there yet. |
| `chain-conveyor-shots.json` | Grabs the chain (the crosshair must be on the strand itself: z 337.3, straight up) and shoots the hang from front and back. |
| `packs-vanilla / packs-fa / packs-fa-player.json` | The three pack configurations to shoot it in. |
| `parcool-course.json` | NeoForge 1.21.1 `Test`, world `New World2`: unlocks every ParCool 4 action, builds a runway with a low wall (vault), a 4-high wall (hang, climb up) and a long wall. Launch with `enable=["ParCool-1.21.1", "emf_compat_parcool"]` and `disable=["hackersandslashers-2.0", "emf_compat_hackers_and_slashers"]` (Better Combat does not load next to Hackers 'n Slashers). |
| `parcool-moves.json` | Fast run in/hold/out with fade probes, vault, hang and climb up, crawl, slide, dodges, charge jump — each with the `parcool` probe. |
| `parcool-pack.json` | The ParCool animation pack's moves with the pack on: fast run and charge jump (`fade` shows no pose sources while the pack animates them), a vault that stays ParCool's pose, then the hang - a drop onto the wall (ParCool slides down first, on the same key), a shuffle, and a free ledge. Build the pack first (`extensions/parcool/resourcepack/build_pack.py`), launch with WATUT disabled, hide the GUI and enable it above FA+Player with a `packs` step. |
| `parcool-all-scene.json` | A sky course at y=200 (floor at 199, nothing else in view) for every ParCool 4 action: a runway ending at the platform edge (dive, skydive), a low wall (vault), a 3-high ledge (hang, shimmy, climb up), a 9-high wall (catch, castaway, wall run, grapple), a long wall (side wall run, wall jump), an end-rod bar (hang down), a chain pole, a hay block (hide), a pool (fast swim) and two zipline hooks. Unlocks the actions. |
| `parcool-all-moves.json` | All 27 moves on that course, each marked `move <name>` in the log, with a `parcool` probe and a screenshot every few ticks: fast run, slide, crawl, four dodges, charge jump, back and forward trick jumps, vault, hang/shimmy/climb up, castaway, catch from a drop, wall run, wall jump, side wall run, dive, skydive, both breakfalls, hang down, pole climb, hide in block, fast swim, zipline (ties the rope first), grapple. Keys are held for 2 ticks, not clicked: ParCool reads key state per tick and misses a `click`. ParCool 4.0.0.3 itself logs a `ClassCastException` from `Castaway.onStart` on the integrated server; the move still plays. |
| `parcool-obstacle-scene.json` / `-run.json` | An obstacle course in one lane (x -530..-508, z 445 to 195, y 200) next to the sky course, and one run through it from start to finish with no teleports: run, vault, slide under a gap and crawl out, jump a lava pit, charge jump onto a 1.5-high step, dodges, catch a 3-high ledge, shimmy and climb, wall run up (lava before the wall) and catch, an 8-block drop with a roll, side wall run over lava and wall jump, a short bar over lava, chain up a wall to its top, round a post, zipline over a lava lake to a tower, dive into a pool, swim, climb out, hide in hay, a bar across the lane (grab with a run-up, shift right, swing forward-back twice, let go), finish. Lava keeps solid edge columns with barriers on them, so it neither pours off the sides nor can be walked round. The run waits on positions with `until`, so its timing does not hang on frame rate; it needs longer than the MCP call allows - run it with `mctest.py steps` or `run_steps` (the timeout grows with the script). Generated by `parcool_obstacle.py`; edit that, not the JSON. What the course relies on: sprint is ~0.3 block/tick; the charge jump tops out at +2.2 and only lands from right against the step; the hang key shares the slide-down binding (held on the ground by a wall it slides down); breakfall must be pressed in the air; the wall run starts ~2.5 blocks from the wall; the side wall run needs a small gap to the wall (0.15 worked, touching or 0.5 did not); ParCool refuses a rope whose sag touches a block, the tiny hooks are aimed at from their own height, and a platform edge more than ~1.5 blocks past the grab stops the ride; a swing is a pendulum pushed by forward/back, one key per half swing. Jumping off a swing was dropped: ParCool takes it only above 0.157 rad/tick at a narrow angle, and scripted presses hit it only now and then. |
| `aa-scene.json` / `aa-shots.json` | **Animation Additions baseline** (see the section below). A course floating at y 150 over x 300..360, z 0..30 in any world, and every feature shot on it, each case marked `aa <case>` in the log. The shots are long: run them with `mctest.py steps Test "$(cat scenarios/aa-shots.json)"`. Generated by `animation_additions.py`; edit that, not the JSON. |
| `demo-pad-carryon.json` / `-hns.json` / `-parcool.json` | Short takes for the store-page GIFs, in world `test` on the clearing at -2 79 0 that the published GIFs use. The player starts on the same mark and stance every time (`-2.5 79 0.5`, yaw 114) with the camera third person **front**, and takes are separated by a still second to cut on; each is marked `demo <take>` in the log. The ground west of the mark is levelled first — that is where the props go and where the player walks, and it is behind the lens. Carry On lifts a chest on **sneak + use** (plain use opens it), carries it and sets it down. Hackers 'n Slashers needs `enable=["hackersandslashers-2.0", "emf_compat_hackers_and_slashers"]` (both are `.disabled` in the profile) and shows the stance, a four-hit combo and a block. ParCool cannot use the front camera at all — it stands where the player is running and ends up inside the props — so its takes use the orbit camera side-on; the framing there still needs work. Generated by `demo_pad.py`; edit that, not the JSON. |
| `parcool-x-*.json` | The cross-mod pass: one file per addon (`carryon`, `create`, `tacz`, `nea`, `bettercombat`, `hns`, `misc`), each pairing a ParCool carrier (fast run, crawl, charge jump, bar hang, ledge hang) with that addon's action and probing `fade` + `parcool` around it. Run `parcool-all-scene.json` first. **Two settings decide whether the run means anything**: the sandbox copies the profile's `config/emf_compat.json`, where `parcool.enabled` may well be false, and a plain launch selects no EMF pack at all — so set `{"config": {"parcool.enabled": true, "parcool.wholePose": true}}` and a `packs` step with FreshAnimations, FA+Player and `mod/emf_compat_parcool:resourcepacks/parcool_animations` before the first scenario, or the whole pass measures ParCool against the other mods with our module switched off. Better Combat and Hackers 'n Slashers each need their own launch (`enable=[...]`) and do not load together. Generated by `parcool_crossmod.py`; edit that, not the JSON. |
| `parcool-combos.json` | The same course with other addons: a Better Combat swing during a fast run (add `bettercombat-neoforge`, `emf_compat_better_combat` to `enable`), an Immersive Melodies flute while running and crawling, NEA eating into a run, WATUT typing, the JustExpressions face during a charge jump. |

For contact sheets and images outside MCP, import `server.py`:
`uv run --with "mcp<2" --with pillow python -c "import sys; sys.path.insert(0,'tools/mctest'); import server; ..."`.

**Build first.** The sandbox takes our jars from `upload/`, which is filled by `./gradlew build`.
A driver change needs `./gradlew -p tools/mctest/driver build`. Neither is run automatically.

## Animation Additions baseline

**Since 05.10.2026.** The decision lines (`[FootGrounding] ... stride`, `[ButtonPress] ... hover-R`) are behind
`debug.decisions`, off for players. `mctest.py launch` sets it in the sandbox's copy of `config/emf_compat.json`;
the MCP server keeps the `mctest.py` it started with, so until it is restarted a launch through MCP does not -
turn it on with `{"config": {"debug.decisions": true}}`. Three trace switches exist: `footgrounding.trace`,
`wallhand.trace`, `transport.trace`; `interaction_regression.scene()` turns some on, so a timing run sets all
three to false. Do not run a `packs` step before the baseline: the profile's own selection is the right one, and
`"FA+Player"` matches the folder `FA+Player-v1.1 CSA Compat` rather than `FA+Player-v1.1.zip`.

Run to run on one build the decisions differ only in `horse/rider-tilt` (`off:state` or `off:airborne` at the
mount), the horse's lines in `horse/ride-stairs`, `WallHand front` on the walk up to the wall button, and
`chest` / `chest-open`. A difference anywhere else is real.

Two cockpit verifiers are not a regression signal as they stand: `verify_cockpit.py` fails with "Camera hover
freed a hand" whenever the first trace sample of `right` / `left` falls between the look and the hold, and
`verify_cockpit_mixed.py` reports `left-typing-again` at 0.137-0.151 against its 0.13 on every build.
`verify_cockpit_smooth.py` needs the reference gallery `build/cockpit-seat-clearance-mixed`.


Retaken 25.09.2026 on `experimental/animations-additions` at `66f1ed7` (first taken at `893a5d0`),
NeoForge 1.21.1, profile `Test` launched with `--name STRadaT --uuid
e750dfddf54d418babd46776ce404f09 --disable icys-better-horses,watut,emf_compat_watut`, packs Fresh
Animations + FA+Player + the ParCool pack. Contact sheets are in `baseline/animation-additions/`:
`still.jpg` - every standing case with the feature on and off; `contact.jpg` - hands on things
(the `contact/*` cases, taken 26.09.2026 at `e35bf32`, run alone with `aa-contact.json`); `stairs.jpg` / `slabstairs.jpg` -
the climbs every second tick; `slab-on.jpg` / `slab-along.jpg` - onto a slab facing it and along its
edge, every tick; `motion.jpg` - the wheat walk and riding up the stairs. Before changing anything,
run the two scripts again and compare with these. Per-frame numbers: turn on
`footgrounding.trace` (a `config` step) and read `[FootTrace]` in `latest.log`.

What each case shows, and what the log said (`[FootGrounding]` etc. print on every change):

| Case | Expected | Measured |
|---|---|---|
| `foot/slab-edge` | one foot on the slab, the body lowered onto the other | `lowered (R=8.55 L=0.02)` |
| `foot/snow-edge` | the same over 3 snow layers (collision 4 px) | `lowered (R=4.28 L=0.02)` |
| `foot/slab-edge-armour` | leggings and boots follow the raised leg | the mark lands in the dead zone (hitbox on the slab, both soles off it): `reach-R` - a foot reaches out onto it |
| `foot/slab-step-on` | walking onto the slab: the leading foot up first, the body rises as the weight goes over | `stride`; the weight share (`w` in the trace) swings 0..1 with the stride |
| `foot/slab-along` | along the edge: the body rises onto the upper foot while it carries the weight and sinks with the lower one | `stride`; body 0..8.5 px with each step |
| `foot/stairs-walk`, `foot/slab-stairs-walk` | the foot on the next step carries the body up; never both feet lifted | `stride`; both legs up by more than 1.5 px in 0-4 frames a climb, at most ~1.8 px (was 5-16 frames, 3.3 px) |
| `foot/crouch-flat` | the crouch leaves the legs to the pack (the render offset is not a floor) | `flat (R=0.00 L=0.00)` |
| `wall/front` | both palms on the wall | `front` |
| `wall/side-with-wheat` | the wall hand on the wall, the other in the wheat | `right`, then `right_arm: WallHand -> PlantReach` when the wall is lost |
| `plants/field-centre` | both hands in the wheat | `RL` |
| `plants/field-walk` | hands follow the wheat walking | `--` at the start (the frames are at the field edge) |
| `lookat/idle` | after 3 s idle the head turns to the villager, the torso a little after it; back when the camera moves | `-> Villager`, then `camera` |
| `horse/standing` | across the edge: body pitched; along it: the left legs slide up | the tilted horse is fine; the other logged `body=-90` - see below |
| `horse/rider-tilt` | the rider pitches with the horse | seen in the frame |
| `horse/ride-stairs` | the horse pitches up the stairs, the rider with it | `lowered`, `tilted ... tilt=11.0` |
| `contact/wall-button` | the right hand points at the button looked at, onto its middle pressed | `hover-R`, `press-R` |
| `contact/lever` | the hand on the end of the handle, over with it both ways | `press-R` on each throw; the hover is not caught from this mark (the look is at the edge of the 30° cone) |
| `contact/floor-button` | a foot over the button, down on it pressed | `foot-R`, `stamp-R` |
| `contact/door-shut` | at a shut double door each hand on its leaf's handle | `both` |
| `contact/door-through` | walking through the open double door both hands hold the leaves | `both` from the approach, `none` past the door |
| `contact/chest` | the hands on the lid; open, the left one holds it up, the right one on the chest | `chest`, `chest-open` (the chest's screen hides the player: judged by the log) |
| `contact/lectern` | the hands on the book | `lectern` |

Known defects and traps found while taking it:

- **Dead zone:** the hitbox is 0.6 wide and rests on a step its edge touches, while the soles
  (near the body's axis) are both off it. Standing there, a foot now reaches out to the step -
  forwards or out to its own side, never behind. Marks near an edge land in it easily.
- **A teleport turns the head only.** The body keeps its old heading until the player moves, and
  the feet, the wall hand and the plant reach all measure along the body. The cases take a
  3-tick step first (`settle`); a mark a few hundredths too far from an edge still reads both
  feet on one side (the probes reach 0.105 blocks around each foot centre).
- **An arm reaches ~0.6 blocks.** A side wall farther from the shoulder than that is `none`.
- **NoAI horses turn their body to the head over time** (`aa_side` logged `body=-90` although
  summoned facing south), so the "along the edge" horse ends up across it.
- **Horse at the top of the stairs:** the hind hooves two steps down read 17 px, past
  `MAX_STEP`, and the horse goes `flat` for a moment - a visible drop.
- **The ridden test horse bucks** (angry particles): tamed by NBT without an owner.
- **The game pauses when its window loses focus** (the pause menu opens and nothing runs):
  start a run with `{"closeScreen": true}`.
- WATUT must be off (AFK "zZ" and a bowed head), `icys-better-horses` too (it needs modonomicon,
  which the profile lacks).
- Hackers 'n Slashers flings the legs on attacks straight up or down - upstream, see the To-Do.

## Steps

A script is a list of steps, run in order on the client thread. `wait` counts client ticks (20/s).

```
{"cmd": "time set noon"}        run a command      {"chat": "hi"}
{"hold": "sneak"} {"release": "sneak"} {"releaseAll": true}
{"click": "attack"}             one press          {"slot": 0}
{"look": [yaw, pitch]}          pitch > 0 is down  {"camera": "first|back|front"}
{"hideGui": true}               (not on 26.2)      {"closeScreen": true}
{"wait": 10}                    ticks              {"log": "marker into latest.log"}
{"state": true}                 pose, hands, screen, crosshair target, held keys
{"screenshot": "name"}          the LAST RENDERED frame
{"fade": true}                  pose sources + each part's fade weight
{"config": {"core.smoothPoseTransitions": false}}   core options, in memory only
{"packs": ["FreshAnimations", "FA+Player"]}        resource packs, in order; the rest off
{"model": "villager"} {"model": {"entity": "player", "depth": 3}}   the renderer's model tree
{"burst": {"count": 8, "every": 1, "name": "atk", "fade": true}}   expanded by server.py and the CLI
{"until": {"z<": 413.7, "onGround": true, "timeout": 80, "shots": "pit", "every": 2}}   waits until all hold
    (x/y/z with < or >, onGround, swing< / swing> = ParCool bar swing rad/tick) or the timeout; no condition
    = a plain wait. Shoots every N ticks meanwhile; the result has ticks, pos, swing and timedOut (NeoForge 1.21.1)
{"orbit": [90, 10, 5]}          camera at [yaw offset, pitch, distance] around the player; false = off
{"orbit": [0, 80, 3, true]}     the same, pinned in the world where the player is now (stops following)
{"parcool": true}               ParCool 4: running animations, overwriting/blend factor, driven parts
{"bot": {"spawn": "Bob", "at": [x,y,z], "look": [yaw,pitch]}}   another player (NeoForge 1.21.1), see "Multiplayer bot"
```

`orbit` and `parcool` exist in the NeoForge 1.21.1 driver only (the orbit is a `Camera.setup` mixin,
the driver's only one). The yaw offset is relative to where the player faces - `0` is behind it, `180` in front - so `90` stays side-on
while it runs and turns — the only way to see a lean, since `front`/`back` look along the movement.

The Create hat fix has its own switch, so one run can shoot both states without a reload:
`{"config": {"create.hats": false}}` → screenshot → `{"config": {"create.hats": true}}` → screenshot.

`packs` is how one run shoots the same scene with and without a pack: a name is matched against the
pack ids exactly or as a substring, `vanilla` is always kept, and the reload starts *after* the
script answers — so put it last in its own call and let the next call be the wait (a reload of a
large profile takes tens of seconds; `mc_status` goes stale meanwhile, which is the signal).

`model` is the probe for "the worn thing sits in the wrong place": it prints every `ModelPart` field
of the model the renderer will use — the class it really is, how many cubes it still has, and its
transform. Under EMF a part the pack replaced reports `cubes: 0` (the geometry moved into a custom
child), which is what mods measuring the model fall back from.

Keys: `forward back left right jump sneak sprint attack use drop swap inventory`, or any mapping by
its translation key (`key.carry.desc`).

## How it works

**Launch.** Profile → loader and version from the Modrinth App database
(`~/Library/Application Support/ModrinthApp/app.db`, tables `instances` + `instance_content_sets`;
`instance_launch_overrides` holds memory and extra JVM args as SQLite JSONB — decode with `json()`
from Python's sqlite3, the system `sqlite3` CLI is too old). The flattened version JSON lives in
`~/Modrinth/meta/versions/<id>/`; the classpath is its libraries minus `include_in_classpath: false`
(installer-only jars that break NeoForge/Forge if included), plus the version jar. Java comes from
`~/Modrinth/meta/java_versions/`. The game starts offline as `Dev` with `--accessToken 0` and
`--quickPlaySingleplayer <world>`: **no account token is ever read.** Everything the profile had
already downloaded is reused, so nothing is fetched.

**Sandbox.** Mods are symlinked from the profile, except our `emf_compat_*` jars, which are copied
from `upload/` by matching `emf_compat_<addon>_<mcversion>_`. The driver jar is copied in. `config/`
is copied, `resourcepacks/` and `shaderpacks/` linked, `options.txt` copied and patched (no pause on
lost focus, sound off, chat hidden, no toggle crouch/sprint). One world is copied on first use;
`fresh_world=True` copies it again. `.disabled` mods stay disabled, unless `enable=["artifacts"]`
names them — then they are linked into the sandbox under their enabled name, and the profile still
keeps its `.disabled` file. `disable=["punchy"]` (CLI `--disable punchy`) does the reverse for one run:
Punchy takes over first-person hand rendering, so nothing hooked into `PlayerRenderer.renderHand`
runs while it is installed.

**Driving.** The driver polls `<sandbox>/mctest/inbox/*.json` every client tick, runs the steps and
writes the answer to `outbox/`; both sides write to a temp file and rename, so half-written files are
never read. `status.json` is refreshed every 10 ticks (`inWorld`, `screen`, `fps`) — that is how
`wait_ready` knows the world is up, and how it spots a loader error screen instead of timing out.
Held keys are re-asserted every tick, because opening a screen releases every mapping.

**Screenshots** go through vanilla `Screenshot.grab` into the sandbox's `screenshots/`, so no screen
recording permission is involved. They show the frame rendered *before* the step, so leave a tick
between changing something and shooting it. `server.py` crops around the player and, for several
frames, builds one contact sheet — one image instead of eight.

**The `model` probe** reads the model the renderer holds. It finds the model by field *type*, the
parts by walking the root's children, and `cubes`/`children` by their generic type — names, whether
of fields or methods, are only readable where the game runs on official mappings (NeoForge 1.21.1),
and would be `field_3661` on Fabric and `field_78116_c` on Forge.

**Core probes** (`fade`, `config`) reach the core by reflection, so the driver compiles against
nothing of ours and works with any core version. `fade` reports the pose sources on the player and
each part's interpolator weight as `"0.62 in"` / `"0.35 out"`. Fade in and out take 4–5 ticks; with
smoothing off the sources are still there but no weights appear at all.

## Adding things

**A step.** Add a `case` to `run(...)` in `driver/*/src/main/java/strm/mctest/Driver.java` — all five
copies; they differ only in the version-specific lines listed below. Document it in the `mc_steps`
docstring in `server.py` and in the table above. Rebuild the driver.

**A probe into our own code.** Use reflection (see `fade`/`config`), never a compile dependency:
the driver has to load next to whatever core version is installed in the profile.

**A target.** Create `driver/<loader>-<mc>/` with `build.gradle`, the loader entry point and
metadata, and a copy of `Driver.java`. `mctest.py` finds it by the folder name
`<loader>-<gameversion>`, so it must match what `mc_profiles` prints. Known differences:

| Version | Difference |
|---|---|
| 1.21.11+ | `Inventory.get/setSelectedSlot`, `ResourceKey.identifier()`, `Screenshot.grab(..., int downscale, ...)` |
| 26.x | `Level.getOverworldClockTime()` instead of `getDayTime()` |
| 26.2 | `mc.gui.screen()/setScreen`, `gameRenderer.mainRenderTarget()`, no `Options.hideGui` |
| Forge 1.20.1 | needs `pack.mcmeta` in the jar, or a warning screen blocks quick play |
| everywhere | `KeyMapping.getKey()` is gone on new versions — use `InputConstants.getKey(mapping.saveString())` |

## Traps worth remembering

- **Left click on a block in reach is mining, not attacking.** Better Combat plays nothing and
  vanilla just swings. Check `state.target`; aim up (`{"look": [180, -60]}`) or clear the area
  (`fill ~-5 ~ ~-5 ~5 ~3 ~5 air`). Crouching lowers the eyes onto grass.
- **ParCool 4 needs its skill tree unlocked** (`parcool action unlock @s all`), or no action starts
  and nothing tells you why. Keys: crawl `key.parcool.crawl` (C), hang `key.parcool.hang` (right
  mouse), dodge/breakfall/side wall run `key.parcool.dodge` (R). A hang only catches when the top of
  the hitbox is within ~0.2 of the ledge and the wall within 0.15: for a wall topping out at y=-56,
  `tp @s <x> -57.8 <wall face + 0.35>` while holding the hang key. A chat screen releases ParCool's
  own key state, so a hang ends when chat opens.
- **`look` straight after `tp` is lost.** The teleport's rotation reaches the client later and
  resets the pitch, so the player looks level whatever `look` said. Put a `{"wait": 20}` between them.
- **A carry survives `clear @s`**, and a new pick-up while one is held does nothing. Start a Carry On
  script with `carryon clear @s`.
- **A pack that fails EMF's ASM compile is dropped whole**, and `latest.log` only says
  "Failure parsing ASM". The reason goes to stdout: read `run/mctest/<profile>/mctest/launcher.out`
  (e.g. "a variable was used both as a number and a boolean" for `!var.x`). `launch --emf-log`
  also turns on EMF's model-creation and ASM logs in the sandbox's config copy.
- **The default skin and a cape hide limbs in shots.** `launch --no-cape` turns the player's cape
  off in the sandbox's options; `--name NAME --uuid UUID` (MCP: `mc_launch(name=, uuid=)`) plays as that account (UUID from
  `api.mojang.com/users/profiles/minecraft/NAME`), and the game fetches its skin. `--name` alone
  picks another default skin (`--name Player` is the wide Steve).
- **WATUT marks a scripted player as AFK** ("zZ") and bows its head; shoot animations with
  `disable=["watut", "emf_compat_watut"]`. A pack switch while the chat renders can crash vanilla's
  font upload - hide the GUI first.
- **The `front` camera** puts the camera in the direction the player looks, so on screen the player
  faces you and looks at whatever is *behind* them. The camera also collides with a block it runs
  into — a chest two blocks ahead fills the frame with a close-up.
- **Carry On** picks up with its own mapping (`key.carry.desc`) on shift, so `hold` presses every
  mapping bound to that physical key, as a real press would. Its `maxDistance` is 2.5 from the feet;
  put the chest right in front (`setblock ~ ~ ~-1 chest`, look ~50° down), and to place it look
  ~35° down while crouching — steeper and the target cell overlaps the player, so nothing is placed.
  A click is consumed at the start of the next tick with the crosshair it had then, so turning away
  one tick after the click keeps the pickup and still gives a clean camera.
- **Chat must not be set to "hidden".** With `chatVisibility:2` the server answers every `cmd` step
  with "Chat disabled in client options" and runs none of them — silently, since the chat is hidden.
  The sandbox is written with `1` (system messages only) and screenshots are taken with `hideGui`.
- **`server.py` is a stdio server.** Running it by hand without a client just hangs waiting on stdin.
- The game may be closed by hand at any time; `mc_steps` then answers `game exited` with a log tail.

## Experimental foot IK regression (29.09)

`scenarios/footik_regression.py` generates `footik-scene.json` and `footik-cases.json` (named takes).
Eight FA+Player cases, per-tick screenshots, `footik START/END` log markers and richer FootTrace.
`footik_report.py` summarizes contacts and flags body lag / both legs raised; check pose/stride
sample counts before treating a run as valid. Exact setup, results and limitations:
[footik-review.md](footik-review.md).

## Interaction regression (30.09.2026)

`scenarios/interaction_regression.py` generates `interaction-scene.json` and a dictionary of 29
named takes in `interaction-cases.json`. Run the scene once, then concatenate selected takes for
`mc_steps`. Save its JSON text as `build/interaction-review/*-results.json`, and copy the final
`latest.log` to `build/interaction-review/after.log`. Run `interaction_report.py` with that folder
and the sandbox screenshots directory to build an HTML frame gallery and check the measured
Crank invariants. Read [interaction-review.md](interaction-review.md) for the exact results,
remaining visual defects, and fixture limitations. Tests distinguish gameplay crouch from visual
extension and explicitly do not classify the low-crank contact as passing.

### Wheel and unobstructed contact review (NeoForge 1.21.1)

Generate the eight cases with `python3 tools/mctest/scenarios/wheel_regression.py`.
Run `wheel-scene.json`, then the cases from `wheel-cases.json`; save the result text
as `wheels-results.json` (first four) and `contacts-results.json` (last four).
Copy `latest.log` as `after.log` and build the gallery with:

```sh
python3 tools/mctest/interaction_report.py build/wheel-review run/mctest/Test/screenshots --cases tools/mctest/scenarios/wheel-cases.json
```

Use `{"hideScreen": true}` as well as `{"hideGui": true}`: the former suppresses
screen rendering while keeping the actual container open, including chest lid state.
It affects the test driver only. Restore with `{"hideScreen": false}`.
Place the player near the physical arm contact limit, rather than the gameplay use
range. Aim at the front face of a bookshelf slot; aiming at the block centre can hit
the wrong row at close distances. Review the palm in side views as well as metrics.

`{"steeringDrag": 50}` sends horizontal mouse motion to Aeronautics' active steering
handler (about 5 degrees). Click the rim first; the step fails if no steering interaction
is active. Move away before starting the next case to end its persistent hold.
Target states expose `getIndependentAngle` in degrees and `getRenderAngle` in radians.
See `wheel-review.md` for verified coverage and remaining limitations.

## Multiplayer bot (NeoForge 1.21.1, 30.09.2026)

`{"bot": ...}` puts a second player on the integrated server: a `ServerPlayer` on a connection with
nothing behind it (`Bot.BotConnection`, as Carpet's `/player`). Our client gets it through the
ordinary packets and draws it as a `RemotePlayer` - the path a real second player takes, so
anything keyed on `mc.player` (the crosshair, held keys) is **not** available for it.
`mixin/BotListenerMixin` drops everything the server sends it: NeoForge throws on mods' payloads
to a connection that never negotiated their channels, and the keep-alive would time it out.

```
{"bot": {"spawn": "Bob", "at": [345.3, 150, 10.5], "look": [-90, 0]}}   (re)spawn, stands on ground
{"bot": {"at": [...], "look": [yaw, pitch], "sneak": true, "item": "minecraft:stick"}}
{"bot": {"use": [346, 151, 10], "face": "west", "hit": [x, y, z]}}       right click + swing, returns the result
{"bot": {"at": [...], "lookAt": [x, y, z]}}                              look at a point from the eyes there
{"bot": {"cycle": {"use": [x, y, z], "face": "south", "every": 40, "offset": 0, "close": 20}}}
                                     the same click every 40 ticks on the server tick, between scripts too;
                                     a container shut again 20 ticks later; "cycle": false stops it
{"bot": {"menu": {"slot": 1, "button": 1, "type": "PICKUP"}}}   a click on a slot of the menu a "use" opened;  {"bot": {"close": true}}
{"bot": {"swing": true}}   {"bot": {"remove": true}}                     "name": "Bob" when there are several
```

**Extending EMF ATLAS**: the stands are generated - edit `polygon/build_polygon.py`, then run it with
`ATLAS_WORLD="run/mctest/Test/saves/EMF ATLAS - Animation Campus"` to write the pack into the map
itself; in game `/reload` and `/function emf_atlas:interactions` adds zone 03's second row (z 2097:
cauldrons, hive and nest, candle cake, candles, TNT, crafter, enchanting and cartography tables, a
barrel with what they take) without rebuilding the rest. `/function emf_atlas:create_items` adds zone 04's row (z 2137: depot, item drain, basin, blaze burner, a barrel of items and fuel; z 2140: the value boxes - speed controller, creative motor, brass funnel, content observer, redstone link). z 2132: a seat behind a steering wheel; z 2145: contraption controls, train controls and a barrel with what a train takes. z 2149 and z 2153: the rest of Create and Aeronautics (crafter, deployer, packager, frogport, postbox, ejector; gearshift, display link, stock ticker, requester, bearing, arm, typewriter, navigation table, rope winch, potato cannon, burner). A bot swings its arm on every `use`, so a swing-told tap always shows for it. Our own player's interactions are only computed while it is drawn: test them in third person (`camera: back`). `/function emf_atlas:supplementaries` adds zone 07's Supplementaries stands (rows z 2194 and z 2206, three more at z 2201; a barrel of what they take at 2088 151 2201). `/function emf_atlas:restock` (and the
button by the row) puts back what a use takes away. A command block under the TNT kills it lit.
A bot's `"item"` is sent to the clients by the step itself - nothing ticks a bot.

**Performance runs.** `{"maxFps": 260}` takes the frame cap off (260 is unlimited; in memory only), `{"frames": "start"}` begins recording every frame's length and `{"frames": "report"}` gives the frames counted, mean fps, the median frame and the slowest 1% and 0.1% in ms. The frame rate drifts by a fifth and more within one launch as the game warms up, and differs between launches: compare **within one launch**, switching what is measured with `config` and going A, B, A, B at least three times, 15 s each after a 15 s warm-up. Launch-to-launch numbers only tell large differences. Variants by jars: `disable=["emf_compat_animation_additions"]` (EMF Compat without Animation Additions), `disable=["emf_compat"]` (none of it; leave the `config` step out).

**Ten bots in EMF ATLAS** (world `EMF ATLAS - Animation Campus`, zone 03 INTERACTIONS; the map is
built by `polygon/build_polygon.py` + `detail_polygon.py`): `scenarios/atlas_bots.py` writes
`atlas-bots.json` (spawn and start) and `atlas-bots-stop.json`. Wall stone/oak buttons and a lever,
the floor button (by foot), a floor lever, the oak door, a chest opened and shut, a repeater, a
comparator and a note block, each on its own period so the presses do not line up. Checked
30.09.2026: every bot's provider decides as expected (`press`, `stamp`, `tap`, `chest-open`, door
hands), no exceptions. The floor button also toggles the trapdoor next to it by redstone.

`{"menuClick": {"slot": 1, "button": 1, "type": "PICKUP"}}` clicks a slot of our own open menu, as the mouse does (`QUICK_MOVE` for shift-click); with `hideScreen` the menu stays open unseen. It does not move or fall by itself: each step places it (`moveTo`, on ground). Its skin is the
offline default for its name. A Sable craft's hold interactions (the steering wheel) are
client-driven, so a bot cannot steer; turn the wheel with our own player (`click` + `steeringDrag`)
while the bot holds it. `state.target.world` gives where the crosshair hits in the world, also on a craft.
