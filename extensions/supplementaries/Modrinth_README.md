# EMF Compat: Supplementaries

Makes Supplementaries item-use poses work correctly with animated EMF player models.

Brings **Supplementaries** item animations to **Entity Model Features**.

When a resource pack replaces the player model with an animated Entity Model Features variant, the arm poses from Supplementaries items (Flute, Slingshot, Bubble Blower) are often overwritten by the resource-pack animation. This addon captures the arm poses after Supplementaries sets them, then restores them after EMF runs so the player keeps the correct holding posture while using these items.

First-person hand rendering is handled separately so your own item pose stays visible in first person.
