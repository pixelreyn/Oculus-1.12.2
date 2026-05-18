package net.coderbot.batchedentityrendering.mixin;

import net.minecraft.client.renderer.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.ByteBuffer;

/**
 * Accessor-only interface mixin for BufferBuilder's internal ByteBuffer.
 *
 * <p>An accessor interface (even on a class target) makes the Mixin annotation processor emit a
 * refmap entry remapping {@code byteBuffer} -> its SRG name. A plain {@code @Shadow} field would
 * not be added to the refmap and would instead depend on Forge's live deobfuscating remapper
 * being wired into Mixin at apply time -- which is not the case when a coremod (e.g. RealBench in
 * RLCraft) forces BufferBuilder to load during {@code Minecraft.<init>} bootstrap. The refmap
 * entry resolves regardless of apply timing. See {@link MixinBufferBuilder}.
 */
@Mixin(BufferBuilder.class)
public interface BufferBuilderAccessor {
    @Accessor("byteBuffer")
    ByteBuffer getByteBuffer();
}
