package net.coderbot.batchedentityrendering.mixin;

import net.coderbot.batchedentityrendering.impl.MemoryTrackingBuffer;
import net.minecraft.client.renderer.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Makes vanilla {@link BufferBuilder} satisfy {@link MemoryTrackingBuffer}.
 *
 * <p>This class mixin only adds the duck interface and its methods; it deliberately avoids any
 * obfuscation-sensitive member reference (no {@code @Shadow}). The internal ByteBuffer is reached
 * via {@link BufferBuilderAccessor}, whose {@code @Accessor} produces a refmap entry that resolves
 * regardless of when the mixin is applied (see that class for why this matters in heavy coremod
 * environments such as RLCraft).
 */
@Mixin(BufferBuilder.class)
public abstract class MixinBufferBuilder implements MemoryTrackingBuffer {
    @Override
    public int getAllocatedSize() {
        return ((BufferBuilderAccessor) this).getByteBuffer().capacity();
    }

    @Override
    public int getUsedSize() {
        return ((BufferBuilderAccessor) this).getByteBuffer().position();
    }
}
