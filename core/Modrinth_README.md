# EMF Compat: Core

Required shared library that lets EMF Compat addons fix animation conflicts with Entity Model Features.

Shared framework for the **EMF Compat** family of mods.

This module provides the pose snapshot/restore system, first-person checks and the EMF pause/vanilla-model condition API used by every addon. It does not add visible gameplay features on its own, but it is **required** by the Create, Not Enough Animations and other EMF Compat addons.

## What it does

When another mod plays its own player animation, the addon captures the current body pose before EMF overrides it, then restores that pose so the external animation is visible instead of the resource-pack animation.

enjoy ^_^
