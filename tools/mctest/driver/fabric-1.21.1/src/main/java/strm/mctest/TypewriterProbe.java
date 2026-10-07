package strm.mctest;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;

/** Exercise native keyboard associations, entries and packets; never overwrite animation state. */
final class TypewriterProbe {
    private static final String BASE="dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.";
    static JsonObject run(Minecraft mc,String action,JsonElement value) throws ReflectiveOperationException {
        if(!Driver.enabled() || mc.getSingleplayerServer()==null)throw new IllegalStateException("Test copy only");
        Class<?> handler=Class.forName(BASE+"LinkedTypewriterInteractionHandler");
        if(action.equals("typewriterKey")) {
            var a=value.getAsJsonArray();handler.getMethod("onKeyPress",int.class,int.class,int.class,int.class).invoke(null,a.get(0).getAsInt(),0,a.get(1).getAsInt(),0);
        } else if(!action.equals("typewriterState")) {
            var a=value.getAsJsonArray();BlockPos pos=new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());
            // Block use runs on both sides: the client path establishes native input capture.
            if(action.equals("typewriterActivate")) {
                var client=mc.level.getBlockEntity(pos);
                boolean accepted=(boolean)client.getClass().getMethod("checkAndStartUsing",java.util.UUID.class).invoke(client,mc.player.getUUID());
                if(!accepted)throw new IllegalStateException("Native client typewriter refused user");
            }
            try {mc.getSingleplayerServer().submit(()->{
                try {
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var be=p.level().getBlockEntity(pos);
                    if(action.equals("typewriterBind")){bind(be,pos);be.getClass().getMethod("sendData").invoke(be);}
                    else {
                        boolean accepted=(boolean)be.getClass().getMethod("checkAndStartUsing",java.util.UUID.class).invoke(be,p.getUUID());
                        if(!accepted)throw new IllegalStateException("Native typewriter refused user");

                    }
                }catch(Exception e){throw new IllegalStateException(e);}
            }).get(10,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}
        }
        JsonObject result=new JsonObject();result.addProperty("mode",String.valueOf(handler.getMethod("getMode").invoke(null)));
        JsonArray keys=new JsonArray();for(Object k:(java.util.List<?>)handler.getMethod("getPressedKeys").invoke(null))keys.add((Integer)k);
        result.add("pressed",keys);return result;
    }
    private static void bind(Object be,BlockPos pos) throws ReflectiveOperationException {
        Class<?> entry=Class.forName(BASE+"LinkedTypewriterEntries$KeyboardEntry");
        Object entries=be.getClass().getMethod("getTypewriterEntries").invoke(be);
        int[] codes={81,87,69,65,83,68,32};Item[] items={Items.REDSTONE,Items.COAL,Items.DIAMOND,Items.IRON_INGOT,Items.EMERALD,Items.QUARTZ,Items.GOLD_INGOT};
        for(int i=0;i<codes.length;i++) {
            Object key=entry.getMethod("createFromCodec",ItemStack.class,ItemStack.class,int.class).invoke(null,new ItemStack(items[i]),new ItemStack(Items.PAPER),codes[i]);
            entry.getMethod("setLocation",BlockPos.class).invoke(key,pos);
            entries.getClass().getMethod("setKey",int.class,entry).invoke(entries,codes[i],key);
        }
    }
}
