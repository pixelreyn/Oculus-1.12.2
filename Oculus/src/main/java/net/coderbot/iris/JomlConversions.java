package net.coderbot.iris;

import net.coderbot.iris.vendored.joml.Vector3d;
import net.coderbot.iris.vendored.joml.Vector4f;
import net.minecraft.util.math.Vec3d;

/**
 * Utility class for converting between Minecraft and JOML vector types.
 * Adapted for 1.12.2 which uses Vec3d instead of Vec3.
 */
public class JomlConversions {
    public static Vector3d fromVec3(Vec3d vec) {
        return new Vector3d(vec.x, vec.y, vec.z);
    }

    public static Vector4f toJoml(Vector4f v) {
        // In 1.12.2, we just use joml vectors directly
        return new Vector4f(v);
    }
}
