package strm.emfcompat.create.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class CreateEMFCompatMixinPlugin implements IMixinConfigPlugin {
    private boolean aeronauticsLoaded = false;
    private boolean grapplingHooksLoaded = false;
    private boolean ragdollLoaded = false;
    private boolean neaLoaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        aeronauticsLoaded = classLoader
                .getResource("dev/simulated_team/simulated/content/blocks/handle/PlayerHoldingHandleRenderer.class") != null;
        grapplingHooksLoaded = classLoader
                .getResource("com/addon/gancho/AddonGanchoNet.class") != null;
        ragdollLoaded = classLoader
                .getResource("dev/leo/sableplayerragdoll/neoforge/client/RagdollGrabState.class") != null;
        neaLoaded = classLoader
                .getResource("dev/tr7zw/notenoughanimations/animations/hands/ItemSwapAnimation.class") != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals("strm.emfcompat.create.mixin.PlayerHoldingHandleRendererAccessor")
                || mixinClassName.equals("strm.emfcompat.create.mixin.PlayerHoldingHandleRendererMixin")) {
            return aeronauticsLoaded;
        }
        if (mixinClassName.equals("strm.emfcompat.create.mixin.PlayerGrappleHookAccessor")) {
            return grapplingHooksLoaded;
        }
        if (mixinClassName.equals("strm.emfcompat.create.mixin.HumanoidModelRagdollMixin")) {
            return ragdollLoaded;
        }
        if (mixinClassName.equals("strm.emfcompat.create.mixin.ItemSwapAnimationMixin")) {
            return neaLoaded;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
