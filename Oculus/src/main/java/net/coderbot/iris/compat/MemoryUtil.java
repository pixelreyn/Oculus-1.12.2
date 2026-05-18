package net.coderbot.iris.compat;

import sun.misc.Unsafe;
import org.lwjgl.BufferUtils;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Compatibility layer for LWJGL3's MemoryUtil that works with LWJGL2.
 * Uses sun.misc.Unsafe for direct memory operations.
 */
public class MemoryUtil {
    public static final long NULL = 0L;

    private static final Unsafe UNSAFE;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Unable to get Unsafe instance", e);
        }
    }

    // Read operations
    public static short memGetShort(long address) {
        return UNSAFE.getShort(address);
    }

    public static int memGetInt(long address) {
        return UNSAFE.getInt(address);
    }

    public static float memGetFloat(long address) {
        return UNSAFE.getFloat(address);
    }

    public static long memGetLong(long address) {
        return UNSAFE.getLong(address);
    }

    public static byte memGetByte(long address) {
        return UNSAFE.getByte(address);
    }

    // Write operations
    public static void memPutShort(long address, short value) {
        UNSAFE.putShort(address, value);
    }

    public static void memPutInt(long address, int value) {
        UNSAFE.putInt(address, value);
    }

    public static void memPutFloat(long address, float value) {
        UNSAFE.putFloat(address, value);
    }

    public static void memPutLong(long address, long value) {
        UNSAFE.putLong(address, value);
    }

    public static void memPutByte(long address, byte value) {
        UNSAFE.putByte(address, value);
    }

    /**
     * Get the memory address of a direct ByteBuffer.
     * In LWJGL2, we use reflection to access the address field.
     */
    public static long memAddress(ByteBuffer buffer) {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("Buffer must be direct");
        }
        try {
            // In LWJGL2/Java 8, direct buffers have an 'address' field
            Field addressField = buffer.getClass().getDeclaredField("address");
            addressField.setAccessible(true);
            return addressField.getLong(buffer);
        } catch (Exception e) {
            // Fallback: Try sun.nio.ch.DirectBuffer interface
            if (buffer instanceof sun.nio.ch.DirectBuffer) {
                return ((sun.nio.ch.DirectBuffer) buffer).address();
            }
            throw new RuntimeException("Cannot get buffer address", e);
        }
    }

    /**
     * Get memory address with offset
     */
    public static long memAddress(ByteBuffer buffer, int offset) {
        return memAddress(buffer) + offset;
    }

    /**
     * Encode a string as UTF-8 into a direct ByteBuffer.
     * The buffer includes a null terminator.
     */
    public static ByteBuffer encodeUTF8(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length + 1);
        buffer.put(bytes);
        buffer.put((byte) 0); // null terminator
        buffer.flip();
        return buffer;
    }

    /**
     * Allocate a direct buffer
     */
    public static ByteBuffer memAlloc(int size) {
        return BufferUtils.createByteBuffer(size);
    }

    /**
     * Free a buffer (no-op in Java/LWJGL2 since GC handles it)
     */
    public static void memFree(ByteBuffer buffer) {
        // No-op - Java GC handles this
    }
}
