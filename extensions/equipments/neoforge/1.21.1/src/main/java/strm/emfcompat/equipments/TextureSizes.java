package strm.emfcompat.equipments;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Width and height of a texture as the resource manager serves it, read from the PNG header so the
 * image itself is never decoded. Only the render thread asks; cleared on every resource reload.
 */
public final class TextureSizes {

    private static final int[] UNKNOWN = new int[0];
    private static final Map<ResourceLocation, int[]> SIZES = new HashMap<>();

    private TextureSizes() {
    }

    /** {@code {width, height}}, or null when the texture is missing or not a PNG. */
    public static int[] of(ResourceLocation texture) {
        int[] size = SIZES.computeIfAbsent(texture, TextureSizes::read);
        return size == UNKNOWN ? null : size;
    }

    public static void clear() {
        SIZES.clear();
    }

    /** The two sizes are the same shape: a 128x64 texture still fits a 64x32 model. */
    public static boolean sameShape(int[] a, int[] b) {
        return (long) a[0] * b[1] == (long) a[1] * b[0];
    }

    private static int[] read(ResourceLocation texture) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
        if (resource.isEmpty()) return UNKNOWN;
        try (InputStream in = resource.get().open()) {
            byte[] header = in.readNBytes(24);
            // Signature (8), IHDR length (4) and type (4), then width and height, big-endian.
            if (header.length < 24 || (header[0] & 0xFF) != 0x89 || header[1] != 'P' || header[12] != 'I') return UNKNOWN;
            int width = readInt(header, 16);
            int height = readInt(header, 20);
            return width > 0 && height > 0 ? new int[]{width, height} : UNKNOWN;
        } catch (Exception e) {
            return UNKNOWN;
        }
    }

    private static int readInt(byte[] b, int at) {
        return (b[at] & 0xFF) << 24 | (b[at + 1] & 0xFF) << 16 | (b[at + 2] & 0xFF) << 8 | (b[at + 3] & 0xFF);
    }
}
