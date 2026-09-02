package strm.emfcompat.parcool.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Picks the capture path that matches the installed ParCool. The two generations share a mod
 * id but no animation code at all, so the version cannot be told apart by the dependency
 * range - the classes each one ships are the reliable signal.
 */
public class EMFCompatParCoolMixinPlugin implements IMixinConfigPlugin {

    private boolean parCool3Loaded = false;
    private boolean parCool4Loaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        // The client Animation attachment ParCool 3.4.x poses the model through.
        parCool3Loaded = classLoader
                .getResource("com/alrex/parcool/common/attachment/client/Animation.class") != null;
        // ParCool 4's rebuilt animation system, attached to the player itself.
        parCool4Loaded = classLoader
                .getResource("com/alrex/parcool/client/animation/system/IPlayerAnimatorHolder.class") != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals("strm.emfcompat.parcool.mixin.ParCool3AnimationMixin")) {
            return parCool3Loaded;
        }
        if (mixinClassName.equals("strm.emfcompat.parcool.mixin.ParCool4PlayerModelMixin")) {
            return parCool4Loaded;
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
