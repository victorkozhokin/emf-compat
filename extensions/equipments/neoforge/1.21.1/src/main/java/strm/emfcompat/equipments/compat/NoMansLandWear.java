package strm.emfcompat.equipments.compat;

import java.lang.reflect.Field;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.equipments.EquipmentsEMFCompat;
import strm.emfcompat.equipments.PartAttachedModel;

/**
 * No Man's Land's tortoise shell is a plain model placed in the entity's space, not on the torso,
 * with its own fixed crouch tilt. A pack that leans, turns or bends the torso leaves it behind;
 * drawn from the torso, it stays on the back. The mask is a humanoid model and already follows.
 *
 * Found by class name, so nothing of No Man's Land is loaded when it is not installed.
 */
public final class NoMansLandWear {

    private static final Logger LOGGER = LoggerFactory.getLogger("EMF Compat: Equipments");
    private static final String SHELL_MODEL = "com.farcr.nomansland.client.model.tortoise.TortoiseShellModel";

    private static Field shellField;
    private static boolean shellFieldFailed;

    private NoMansLandWear() {
    }

    public static Model attach(Model model, HumanoidModel<?> wearer) {
        if (!SHELL_MODEL.equals(model.getClass().getName()) || !EquipmentsEMFCompat.nmlShellEnabled()) return model;
        ModelPart shell = shellOf(model);
        return shell == null ? model : new PartAttachedModel(model, wearer.body, shell::resetPose);
    }

    private static ModelPart shellOf(Model model) {
        if (shellFieldFailed) return null;
        try {
            if (shellField == null) shellField = model.getClass().getField("tortoiseShell");
            return (ModelPart) shellField.get(model);
        } catch (ReflectiveOperationException | ClassCastException e) {
            shellFieldFailed = true;
            LOGGER.warn("[Equipments] No Man's Land tortoise shell not recognised, left as it is: {}", e.toString());
            return null;
        }
    }
}
