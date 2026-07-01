package strm.createFlyEmfCompat.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class CreateFlyEmfCompatMixinPlugin implements IMixinConfigPlugin {
    private boolean neaLoaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        neaLoaded = classLoader
                .getResource("dev/tr7zw/notenoughanimations/animations/hands/ItemSwapAnimation.class") != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals("strm.createFlyEmfCompat.mixin.ItemSwapAnimationMixin")) {
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
