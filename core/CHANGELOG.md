# EMF Compat Core — Changelog

## Unreleased

- The model no longer flickers for a moment when a Better Combat attack (or another mod's animation) ends - EMF now keeps animating through the whole fade-out, not just the first frame of it

## 2.4.0

- Shared player states for addons and resource packs: what a player is doing (carrying, holding or aiming a gun, and Touch'n Motion's own states) and which parts of the body that takes. Packs read them as EMF variables named emfc_<state>, which read 0 when no mod sets them

## 2.3.2

- An error in this mod while posing a player no longer makes EMF stop animating that player's model for the rest of the session - the pose is skipped for that frame and the error is logged once

## 2.3.1

- Crouch fix no longer moves armour from packs with their own armour models (3D Armor): while crouching, arms and armour stuck out in front of the body

## 2.3.0

- The settings screen is rebuilt: options are sorted into groups you can fold, an option that depends on another sits under it, and the list scrolls when it does not fit
- An option can offer a choice of several values, not only on and off
- An addon's entry in the mod list opens the settings on that addon's tab
- Shared hand and foot reach helpers for addons — Touch'n Motion is built on them
- Added a Fabric 26.3 build
- Your pack keeps animating while another mod plays a Player Animation Library animation — sitting, carrying or playing an instrument no longer freezes the model
- Poses now blend smoothly into your pack's animation instead of snapping. Setting: "Smooth pose transitions"
- Fixed crouching while a mod animates you: no more sinking into the ground or hopping up on every attack. Setting: "Crouch fix"
- Added a Fabric 1.21.1 build

## 2.0.0

- Now requires Entity Model Features 3.3.2 or newer
- Rebuilt on EMF's own animation hooks instead of patching its internals, so pose restoring no longer breaks every time EMF moves something around
- Poses are restored once per rendered player instead of once per body part, which takes work off the render thread

## 1.2.0

- Added support for Entity Model Features 3.3, which moved the state the core reads to restore poses — without this the game crashes the moment a player is drawn
- Still works with EMF 3.2.x, so nothing has to be updated in lockstep

## 1.1.2

- Fixed a frame rate drop in first person — the check for First Person Model ran over and over while drawing the player instead of once, and got slower the more mods were installed (thanks to YEETSKIE1 for the profiling)

## 1.1.1

- Fixed a crash on startup with several addons installed — they registered their settings tabs at the same moment and tripped over each other

## 1.1.0

- Added an in-game settings screen with a tab for every installed addon
- Added a global switch in the Core tab: turn off every addon
- Added body-based arm restore: a held or raised pose keeps its exact shape while you walk, crouch and turn, instead of only its rotation
- Added armour support for full-body poses, so sitting down no longer leaves your armour standing up
- Added limb priority: when two mods reach for the same limb the more specific one wins — an attack takes the arms while a riding pose keeps the legs
- Poses no longer flicker when several mods are active at once