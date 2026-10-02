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
    private static double yaw,phase,radius,omega;
    private static Vector3d centre;
    private static Object ropeBehavior;
    private static boolean ropeControlled;
    private static java.util.List<Vec3> ropeLocal;
    static void ropeTick() {
        if(!Driver.enabled() || craft==null || ropeBehavior==null)return;
        try {
            Object strand=ropeBehavior.getClass().getMethod("getOwnedStrand").invoke(ropeBehavior);
            Class<?> object=Class.forName("dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject");
            var field=object.getDeclaredField("handle");field.setAccessible(true);
            Object original=field.get(strand);if(original==null)return;
            if(!ropeControlled) {
                // Kinematic taut rope fixture: keep native strand networking/interpolation.
                // Sending a second packet for the same tick would leave duplicate snapshots.
                Object fixtureCraft=craft;var localPoints=java.util.List.copyOf(ropeLocal);
                Class<?> handle=field.getType(),sub=Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
                Class<?> pose=Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
                Object controlled=java.lang.reflect.Proxy.newProxyInstance(handle.getClassLoader(),new Class<?>[]{handle},(proxy,method,args)->{
                    if(method.getName().equals("readPose")) {
                        Object transform=sub.getMethod("logicalPose").invoke(fixtureCraft);
                        @SuppressWarnings("unchecked") var vertices=(java.util.List<Vector3d>)args[0];
                        for(int i=0;i<localPoints.size();i++) {
                            Vec3 world=(Vec3)pose.getMethod("transformPosition",Vec3.class).invoke(transform,localPoints.get(i));
                            vertices.get(i).set(world.x,world.y,world.z);
                        }
                        return null;
                    }
                    return method.invoke(original,args);
                });
                field.set(strand,controlled);ropeControlled=true;
            }
            // Physics is paused in this fixture, so explicitly flush the native pose packet.
            // The native source now has the same controlled points: no stale duplicate pose.
            object.getMethod("updatePose").invoke(strand);
            var packet=(net.minecraft.network.protocol.common.custom.CustomPacketPayload)
                    ropeBehavior.getClass().getMethod("makeUpdatePacket").invoke(ropeBehavior);
            net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(packet);
        } catch(Exception e) {ropeBehavior=null;throw new IllegalStateException("Controlled native rope fixture failed",e);}
    }
    static void tick() {
        if(!Driver.enabled() || craft==null || ticks<=0)return;
        try {
            Class<?> sub=Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            Class<?> poseClass=Class.forName("dev.ryanhcode.sable.companion.math.Pose3d");
            sub.getMethod("updateLastPose").invoke(craft);
            Object pose=sub.getMethod("logicalPose").invoke(craft);
            Vector3d position=(Vector3d)poseClass.getMethod("position").invoke(pose);
            if(centre==null)position.add(drift);
            else {phase+=omega;position.set(centre).add(radius*Math.cos(phase),0,radius*Math.sin(phase));}
            ((Quaterniond)poseClass.getMethod("orientation").invoke(pose)).rotateY(Math.toRadians(yaw));
            sub.getMethod("forceUpdateGlobalBounds").invoke(craft);
            drift.add(acceleration);ticks--;
        } catch(Exception e) {ticks=0;throw new IllegalStateException("Fixture motion failed",e);}
    }
    static JsonObject run(Minecraft mc,JsonObject args) {
        if(!Driver.enabled() || mc.getSingleplayerServer()==null)throw new IllegalStateException("Integrated test server only");
        try { return mc.getSingleplayerServer().submit(()->{ try { return server(mc,args); } catch(Exception e) {throw new IllegalStateException(e);} }).get(10,TimeUnit.SECONDS); }
        catch(Exception e) { Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();throw new IllegalStateException("Sable fixture failed: "+cause,e); }
    }
    private static JsonObject server(Minecraft mc,JsonObject args) throws Exception {
        var server=mc.getSingleplayerServer();var level=server.overworld();
        var player=server.getPlayerList().getPlayer(mc.player.getUUID());
        String action=args.get("action").getAsString();
        Class<?> poseClass=Class.forName("dev.ryanhcode.sable.companion.math.Pose3d");
        Class<?> poseInterface=Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
        Class<?> subClass=Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
        if(action.equals("create")) {
            ticks=0;centre=null;ropeBehavior=null;ropeControlled=false;
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
        if(action.equals("aeroRope") || action.equals("aeroRopeMount")) {
            Class<?> behavior=Class.forName("dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior");
            var connector=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("simulated:rope_connector"));
            BlockPos bottom=origin.offset(0,0,1),top=origin.offset(0,5,1);
            var facing=net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
            level.setBlock(bottom,connector.defaultBlockState().setValue(facing,net.minecraft.core.Direction.DOWN),3);
            level.setBlock(top,connector.defaultBlockState().setValue(facing,net.minecraft.core.Direction.UP),3);
            if(action.equals("aeroRope")) {
                Object owner=level.getBlockEntity(top).getClass().getMethod("getBehavior").invoke(level.getBlockEntity(top));
                Object other=level.getBlockEntity(bottom).getClass().getMethod("getBehavior").invoke(level.getBlockEntity(bottom));
                if(!(boolean)behavior.getMethod("createRope",behavior,boolean.class).invoke(owner,other,false))throw new IllegalStateException("Native rope creation refused");
                Object strand=behavior.getMethod("getOwnedStrand").invoke(owner);
                Class<?> ropeClass=Class.forName("dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject");
                ropeLocal=new java.util.ArrayList<>();
                for(Object item:(java.util.List<?>)ropeClass.getMethod("getPoints").invoke(strand)) {
                    Vector3d point=(Vector3d)item;
                    ropeLocal.add((Vec3)poseInterface.getMethod("transformPositionInverse",Vec3.class).invoke(pose,new Vec3(point.x,point.y,point.z)));
                }
                ropeBehavior=owner;
            }
        }
        if(action.equals("circle")) {
            radius=args.get("radius").getAsDouble();phase=0;omega=2*Math.PI/args.get("period").getAsDouble();
            centre=new Vector3d((Vector3d)poseClass.getMethod("position").invoke(pose)).add(-radius,0,0);
            yaw=0;ticks=args.get("ticks").getAsInt();
        }
        if(action.equals("stream")) {
            centre=null;
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
