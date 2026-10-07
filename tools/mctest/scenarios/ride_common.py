"""Shared by the ride scenarios: the same subject shot from four sides, and from above when asked."""

def four_sides(prefix, name, pitch=18, distance=3.6, top=None):
    """Steps: an orbit shot from the side, the front, the back and the other side, named <prefix>-<name>-<side>;
    `top` = (pitch, distance) adds one from above. The orbit is switched off after."""
    out = []
    for angle, label in ((90, "side"), (180, "front"), (0, "back"), (-90, "other")):
        out += [{"orbit": [angle, pitch, distance]}, {"wait": 3}, {"screenshot": f"{prefix}-{name}-{label}"}]
    if top:
        out += [{"orbit": [90, top[0], top[1]]}, {"wait": 3}, {"screenshot": f"{prefix}-{name}-top"}]
    return out + [{"orbit": False}]
