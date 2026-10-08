package strm.touchnmotion.blockuse;

/** Contact first, then weight; unload while the palm is still held before letting go. */
public final class TableSupportMotion {
    float settled, load;
    void advance(float dt, boolean engaged, boolean contact) {
        dt = Math.max(0, Math.min(.1f, dt));
        if (!engaged) { settled = 0; load = Math.max(0, load - dt / .16f); return; }
        settled = contact ? Math.min(.12f, settled + dt) : 0;
        if (settled >= .12f) load = Math.min(1, load + dt / .28f);
        else if (!contact) load = Math.max(0, load - dt / .16f);
    }
}
