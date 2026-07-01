# EMF Compat: Quark

NeoForge 1.21.1 addon that preserves **Quark emotes** when **Entity Model Features (EMF)** player animations are active.

## What it does

Quark adds client-side emotes (wave, salute, dance, custom emotes, etc.) that animate the player model. EMF resource-pack animations normally overwrite those poses. This addon captures the emote pose after Quark applies it and lets EMF Compat Core restore it on top of EMF.

## Supported versions

- NeoForge 1.21.1

## How it works

- `EmoteBaseMixin` injects into `EmoteBase#update` (after Quark's tween update) and saves the full body pose (`head`, `body`, `left_arm`, `right_arm`, `left_leg`, `right_leg`) to `PoseManager` under the source `"quark"`.
- `EmoteHandlerMixin` clears the saved pose when the player has no active emote or the emote has finished.
- EMF Compat Core mixins restore the saved pose after EMF animation runs.

## Dependencies

- `emf_compat_core`
- `quark`
- `entity_model_features`
