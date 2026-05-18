package me.jellysquid.mods.sodium.client.render.chunk.format;

import me.jellysquid.mods.sodium.client.model.vertex.VertexSink;

public interface ModelVertexSink extends VertexSink {
    /**
     * Writes a quad vertex to this sink.
     * @param x The x-position of the vertex
     * @param y The y-position of the vertex
     * @param z The z-position of the vertex
     * @param color The ABGR-packed color of the vertex
     * @param u The u-texture of the vertex
     * @param v The y-texture of the vertex
     * @param light The packed light-map coordinates of the vertex
     */
    void writeQuad(float x, float y, float z, int color, float u, float v, int light);

    /**
     * Writes a quad vertex with extended shader pack data to this sink.
     * @param x The x-position of the vertex
     * @param y The y-position of the vertex
     * @param z The z-position of the vertex
     * @param color The ABGR-packed color of the vertex
     * @param u The u-texture of the vertex
     * @param v The y-texture of the vertex
     * @param light The packed light-map coordinates of the vertex
     * @param entityId The block entity ID for mc_Entity
     * @param midU The mid-texture U coordinate for mc_midTexCoord
     * @param midV The mid-texture V coordinate for mc_midTexCoord
     * @param tangent The packed tangent vector for at_tangent (4x signed byte, normalized)
     */
    default void writeQuadExtended(float x, float y, float z, int color, float u, float v, int light,
                                   short entityId, float midU, float midV, int tangent) {
        writeQuad(x, y, z, color, u, v, light);
    }
}
