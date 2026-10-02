package strm.mctest;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/** Real Sable fixture in the throwaway integrated test server, never an animation override. */
final class CraftProbe {
    private static Object craft;
    private static BlockPos origin;
    private static int ticks;
    private static Vector3d drift=new Vector3d(),acceleration=new Vector3d();
    private static double yaw;
    static void tick() {
        if(!Driver.enabled() || craft==null || ticks<=0)return;
        try {
            Class<?> sub=Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            Class<?> poseClass=Class.forName("dev.ryanhcode.sable.companion.math.Pose3d");
            sub.getMethod("updateLastPose").invoke(craft);
            Object pose=sub.getMethod("logicalPose").invoke(craft);
            ((Vector3d)poseClass.getMethod("position").invoke(pose)).add(drift);
            ((Quaterniond)poseClass.getMethod("orientation").invoke(pose)).rotateY(Math.toRadians(yaw));
            sub.getMethod("forceUpdateGlobalBounds").invoke(craft);
            drift.add(acceleration);ticks--;
        } catch(Exception e) {ticks=0;throw new IllegalStateException("Fixture motion failed",e);}
    }
    static JsonObject run(Minecraft mc,JsonObject args) {
        if(!Driver.enabled() || mc.getSingleplayerServer()==null)throw new IllegalStateException("Integrated test server only");
        try { return mc.getSingleplayerServer().submit(()->{ try { return server(mc,args); } catch(Exception e) {throw new IllegalStateException(e);} }).get(10,TimeUnit.SECONDS); }
        catch(Exception e) { throw new IllegalStateException("Sable fixture failed",e); }
    }
    private static JsonObject server(Minecraft mc,JsonObject args) throws Exception {
        var server=mc.getSingleplayerServer();var level=server.overworld();
        var player=server.getPlayerList().getPlayer(mc.player.getUUID());
        String action=args.get("action").getAsString();
        Class<?> poseClass=Class.forName("dev.ryanhcode.sable.companion.math.Pose3d");
        Class<?> poseInterface=Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
        Class<?> subClass=Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
        if(action.equals("create")) {
            ticks=0;
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"sable paused true");
            Class<?> containerClass=Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
            Object container=containerClass.getMethod("getContainer",Level.class).invoke(null,level);
            Vector3d position=vector(args.getAsJsonArray("pos"));
            Object pose=poseClass.getConstructor(Vector3d.class,Quaterniond.class,Vector3d.class,Vector3d.class)
                    .newInstance(position,new Quaterniond(),new Vector3d(),new Vector3d(1));
            craft=containerClass.getMethod("allocateNewSubLevel",poseClass).invoke(container,pose);
            subClass.getMethod("setName",String.class).invoke(craft,"EMF transport brace QA");
            Object plot=subClass.getMethod("getPlot").invoke(craft);
            origin=(BlockPos)Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot").getMethod("getCenterBlock").invoke(plot);
            ((Vector3d)poseClass.getMethod("rotationPoint").invoke(subClass.getMethod("logicalPose").invoke(craft)))
                    .set(origin.getX(),0,origin.getZ());
            origin=new BlockPos(origin.getX(),0,origin.getZ());
            plot.getClass().getMethod("newEmptyChunk",net.minecraft.world.level.ChunkPos.class)
                    .invoke(plot,new net.minecraft.world.level.ChunkPos(origin));
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)level.setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);
            // Front rail reachable from a shoulder, with two separate grips.
            for(int x=-2;x<=2;x++)level.setBlock(origin.offset(x,1,1),Blocks.IRON_BARS.defaultBlockState(),3);
            subClass.getMethod("getPlot").invoke(craft).getClass().getMethod("updateBoundingBox").invoke(subClass.getMethod("getPlot").invoke(craft));
            subClass.getMethod("forceUpdateGlobalBounds").invoke(craft);
        }
        if(craft==null)throw new IllegalStateException("Create a fixture first");
        Object pose=subClass.getMethod("logicalPose").invoke(craft);
        if(action.equals("stream")) {
            drift=args.has("delta")?vector(args.getAsJsonArray("delta")):new Vector3d();
            acceleration=args.has("acceleration")?vector(args.getAsJsonArray("acceleration")):new Vector3d();
            yaw=args.has("yaw")?args.get("yaw").getAsDouble():0;
            ticks=args.get("ticks").getAsInt();
        }
        if(action.equals("move")) {
            subClass.getMethod("updateLastPose").invoke(craft);
            if(args.has("delta"))((Vector3d)poseClass.getMethod("position").invoke(pose)).add(vector(args.getAsJsonArray("delta")));
            if(args.has("yaw"))((Quaterniond)poseClass.getMethod("orientation").invoke(pose)).rotateY(Math.toRadians(args.get("yaw").getAsDouble()));
            subClass.getMethod("getPlot").invoke(craft).getClass().getMethod("updateBoundingBox").invoke(subClass.getMethod("getPlot").invoke(craft));
            subClass.getMethod("forceUpdateGlobalBounds").invoke(craft);
        }
        if(action.equals("place")) {
            Vector3d offset=vector(args.getAsJsonArray("local"));
            Vec3 local=new Vec3(origin.getX()+offset.x,offset.y,origin.getZ()+offset.z);
            Vec3 world=(Vec3)poseInterface.getMethod("transformPosition",Vec3.class).invoke(pose,local);
            player.teleportTo(world.x,world.y,world.z);
            if(args.has("yaw"))player.setYRot(args.get("yaw").getAsFloat());
        }
        if(action.equals("block")) {
            Vector3d off=vector(args.getAsJsonArray("local"));
            BlockPos pos=origin.offset((int)off.x,(int)off.y,(int)off.z);
            var state=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse(args.get("block").getAsString())).defaultBlockState();
            level.setBlock(pos,state,3);subClass.getMethod("getPlot").invoke(craft).getClass().getMethod("updateBoundingBox").invoke(subClass.getMethod("getPlot").invoke(craft));
            subClass.getMethod("forceUpdateGlobalBounds").invoke(craft);
        }
        JsonObject out=new JsonObject();out.addProperty("id",subClass.getMethod("getUniqueId").invoke(craft).toString());
        out.addProperty("plot",origin.toShortString());out.addProperty("pose",pose.toString());
        if(action.equals("remove"))subClass.getMethod("markRemoved").invoke(craft);
        return out;
    }
    private static Vector3d vector(JsonArray a) {return new Vector3d(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
}
