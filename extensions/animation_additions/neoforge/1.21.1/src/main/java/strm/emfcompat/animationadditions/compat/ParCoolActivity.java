package strm.emfcompat.animationadditions.compat;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import strm.emfcompat.core.PoseManager;

import java.lang.reflect.Method;

/** Optional ParCool bridge: relinquish the entire pose during parkour, including its fade-out. */
public final class ParCoolActivity {
    private record AnimationProbe(Method get,Method state,boolean idle) {
        boolean active(AbstractClientPlayer player) throws ReflectiveOperationException {
            Object animation=get.invoke(null,player);
            return animation!=null && ((Boolean)state.invoke(animation)!=idle);
        }
    }
    private record ActionsProbe(Method get,Method actions,Class<?> continuable,Method doing) {
        boolean active(AbstractClientPlayer player) throws ReflectiveOperationException {
            Object ability=get.invoke(null,player);
            if(ability==null)return false;
            for(Object action:(Iterable<?>)actions.invoke(ability))
                if(continuable.isInstance(action) && (Boolean)doing.invoke(action))return true;
            return false;
        }
    }
    private static final AnimationProbe ANIMATION=animationProbe();
    private static final ActionsProbe ACTIONS=actionsProbe();
    private static boolean warned;
    private ParCoolActivity() {}

    public static boolean active(AbstractClientPlayer player) {
        if(player==null)return false;
        try {
            if(ANIMATION!=null && ANIMATION.active(player))return true;
            if(ACTIONS!=null && ACTIONS.active(player))return true;
        } catch(ReflectiveOperationException | RuntimeException e) {
            if(!warned) {
                warned=true;
                org.slf4j.LoggerFactory.getLogger("EMFCompatAnimationAdditions")
                        .warn("Could not read ParCool activity; falling back to captured parkour poses",e);
            }
        }
        var sources=PoseManager.entitySavedPosesBySource.get(player.getUUID());
        return sources!=null && sources.containsKey("parcool");
    }

    private static AnimationProbe animationProbe() {
        try {
            Class<?> type=Class.forName("com.alrex.parcool.client.animation.system.PlayerAnimator");
            return new AnimationProbe(type.getMethod("get",AbstractClientPlayer.class),type.getMethod("isIdle"),true);
        } catch(ClassNotFoundException | NoSuchMethodException e) {
            try {
                Class<?> type=Class.forName("com.alrex.parcool.common.attachment.client.Animation");
                return new AnimationProbe(type.getMethod("get",Player.class),type.getMethod("hasAnimator"),false);
            } catch(ClassNotFoundException | NoSuchMethodException absent) {return null;}
        }
    }
    private static ActionsProbe actionsProbe() {
        try {
            Class<?> type=Class.forName("com.alrex.parcool.common.Parkourability");
            Class<?> action=Class.forName("com.alrex.parcool.api.action.ContinuableAction");
            return new ActionsProbe(type.getMethod("get",Player.class),type.getMethod("getActions"),action,action.getMethod("isDoing"));
        } catch(ClassNotFoundException | NoSuchMethodException absent) {return null;}
    }
}
