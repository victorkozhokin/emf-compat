package strm.emfcompat.animationadditions.interaction;

/** Small passive-contact stances; never the heavy mechanical-control pose. */
public record ContactStance(float pitch, float yaw, float side, float forward, float spread) {
    public static ContactStance forContact(String source, float x, float weight, boolean crouching) {
        float side = Math.max(-1, Math.min(1, x / 8));
        float posture = crouching ? .45f : 1;
        float pitch, shift, forward, spread;
        switch (source) {
            case "Furniture" -> { pitch = .10f; shift = .30f; forward = -.40f; spread = .55f; }
            case "DoorHold" -> { pitch = .045f; shift = .30f; forward = -.20f; spread = .35f; }
            case "WallHand" -> { pitch = .035f; shift = .22f; forward = -.18f; spread = .30f; }
            case "PlantReach" -> { pitch = .015f; shift = .16f; forward = 0; spread = 0; }
            default -> { return new ContactStance(0, 0, 0, 0, 0); }
        }
        weight = Ease.unit(weight);
        return new ContactStance(pitch * weight * posture, side * .025f * weight * posture,
                side * shift * weight * posture, forward * weight * posture, spread * posture);
    }
}
