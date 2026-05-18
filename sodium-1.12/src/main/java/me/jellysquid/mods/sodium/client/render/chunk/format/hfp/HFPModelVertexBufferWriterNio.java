package me.jellysquid.mods.sodium.client.render.chunk.format.hfp;

import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferWriterNio;
import me.jellysquid.mods.sodium.client.render.chunk.format.DefaultModelVertexFormats;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexUtil;

import java.nio.ByteBuffer;

public class HFPModelVertexBufferWriterNio extends VertexBufferWriterNio implements ModelVertexSink {
    public HFPModelVertexBufferWriterNio(VertexBufferView backingBuffer) {
        super(backingBuffer, DefaultModelVertexFormats.MODEL_VERTEX_HFP);
    }

    @Override
    public void writeQuad(float x, float y, float z, int color, float u, float v, int light) {
        this.writeQuadExtended(x, y, z, color, u, v, light, (short) 0, 0.0f, 0.0f, 0);
    }

    @Override
    public void writeQuadExtended(float x, float y, float z, int color, float u, float v, int light,
                                  short entityId, float midU, float midV, int tangent) {
        this.writeQuadInternal(
                ModelVertexUtil.denormalizeVertexPositionFloatAsShort(x),
                ModelVertexUtil.denormalizeVertexPositionFloatAsShort(y),
                ModelVertexUtil.denormalizeVertexPositionFloatAsShort(z),
                color,
                ModelVertexUtil.denormalizeVertexTextureFloatAsShort(u),
                ModelVertexUtil.denormalizeVertexTextureFloatAsShort(v),
                ModelVertexUtil.encodeLightMapTexCoord(light),
                entityId, midU, midV, tangent
        );
    }

    private void writeQuadInternal(short x, short y, short z, int color, short u, short v, int light,
                                   short entityId, float midU, float midV, int tangent) {
        int i = this.writeOffset;

        ByteBuffer buffer = this.byteBuffer;
        buffer.putShort(i, x);
        buffer.putShort(i + 2, y);
        buffer.putShort(i + 4, z);
        buffer.putInt(i + 8, color);
        buffer.putShort(i + 12, u);
        buffer.putShort(i + 14, v);
        buffer.putInt(i + 16, light);
        buffer.putShort(i + 20, entityId);
        buffer.putShort(i + 22, (short) 0);
        buffer.putFloat(i + 24, midU);
        buffer.putFloat(i + 28, midV);
        buffer.putInt(i + 32, tangent);

        this.advance();
    }
}
