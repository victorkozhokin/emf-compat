# EMF Compat: Carry On

Makes Carry On pick-up poses work correctly with animated EMF player models.

Brings **Carry On** carry animations to **Entity Model Features**.

When a resource pack replaces the player model with an animated Entity Model Features variant, Carry On's raised-arm carry pose is often overwritten by the resource-pack animation. This addon captures the carry pose before EMF applies its animation, then restores it so the player keeps their raised arms while carrying blocks and entities.

Carried objects in third person are also synced to the EMF-animated torso, and carried entities are rendered with the vanilla model so they don't inherit conflicting resource-pack animations. First-person hand rendering is handled separately so your own raised carry pose stays visible.