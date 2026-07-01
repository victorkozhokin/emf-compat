# EMF Compat: Better Combat

Makes Better Combat attack animations work correctly with animated EMF player models.

Brings **Better Combat** weapon-swing poses to **Entity Model Features**.

When a resource pack replaces the player model with an animated Entity Model Features variant, Better Combat's raised-arm charge and swing poses are often overwritten. This addon captures those arm poses after Better Combat sets them, then restores them after EMF runs so attacks look correct. It also scales down the global pitch-based body adjustment and the mace-slam torso tilt so EMF animations remain readable.

Tested with **Fresh Animations: Player Extension**.
